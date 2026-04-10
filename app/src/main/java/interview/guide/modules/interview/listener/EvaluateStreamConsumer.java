package interview.guide.modules.interview.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.service.AnswerEvaluationService;
import interview.guide.modules.interview.service.InterviewAnswerEvaluationTaskService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 面试评估 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行评估
 */
@Slf4j
@Component
public class EvaluateStreamConsumer extends AbstractStreamConsumer<EvaluateStreamConsumer.EvaluatePayload> {

    private final InterviewSessionRepository sessionRepository;
    private final AnswerEvaluationService evaluationService;
    private final InterviewAnswerEvaluationTaskService answerEvaluationTaskService;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public EvaluateStreamConsumer(
        RedisService redisService,
        InterviewSessionRepository sessionRepository,
        AnswerEvaluationService evaluationService,
        InterviewAnswerEvaluationTaskService answerEvaluationTaskService,
        InterviewPersistenceService persistenceService,
        ObjectMapper objectMapper
    ) {
        super(redisService);
        this.sessionRepository = sessionRepository;
        this.evaluationService = evaluationService;
        this.answerEvaluationTaskService = answerEvaluationTaskService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    record EvaluatePayload(String sessionId) {}

    @Override
    protected String taskDisplayName() {
        return "评估";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.INTERVIEW_EVALUATE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "evaluate-consumer";
    }

    @Override
    protected EvaluatePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_SESSION_ID);
        if (sessionId == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new EvaluatePayload(sessionId);
    }

    @Override
    protected String payloadIdentifier(EvaluatePayload payload) {
        return "sessionId=" + payload.sessionId();
    }

    @Override
    protected void markProcessing(EvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(EvaluatePayload payload) {
        String sessionId = payload.sessionId();
        waitForQuestionGeneration(sessionId);

        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdWithResume(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("会话已被删除，跳过评估任务: sessionId={}", sessionId);
            return;
        }

        InterviewSessionEntity session = sessionOpt.get();
        List<InterviewQuestionDTO> questions = parseQuestions(session.getQuestionsJson());
        List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(sessionId);
        questions = hydrateQuestions(questions, answers);

        String resumeText = session.getResume().getResumeText();
        List<InterviewQuestionDTO> evaluatedQuestions =
            answerEvaluationTaskService.evaluateMissingQuestions(sessionId, resumeText, questions);
        persistenceService.updateQuestions(sessionId, evaluatedQuestions);

        InterviewReportDTO report = evaluationService.buildReport(sessionId, resumeText, evaluatedQuestions);
        persistenceService.saveReport(sessionId, report);
    }

    @Override
    protected void markCompleted(EvaluatePayload payload) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(EvaluatePayload payload, String error) {
        updateEvaluateStatus(payload.sessionId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(EvaluatePayload payload, int retryCount) {
        String sessionId = payload.sessionId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_SESSION_ID, sessionId,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("评估任务已重新入队: sessionId={}, retryCount={}", sessionId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新评估状态
     */
    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                session.setEvaluateStatus(status);
                session.setEvaluateError(error);
                sessionRepository.save(session);
                log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
            });
        } catch (Exception e) {
            log.error("更新评估状态失败: sessionId={}, status={}, error={}", sessionId, status, e.getMessage(), e);
        }
    }

    private void waitForQuestionGeneration(String sessionId) {
        for (int attempt = 0; attempt < 120; attempt++) {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return;
            }
            AsyncTaskStatus status = sessionOpt.get().getQuestionGenerationStatus();
            if (status == null || status == AsyncTaskStatus.COMPLETED || status == AsyncTaskStatus.FAILED) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("等待题目生成超时，继续执行报告汇总: sessionId={}", sessionId);
    }

    private List<InterviewQuestionDTO> parseQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return new java.util.ArrayList<>();
        }
        return objectMapper.readValue(questionsJson, new TypeReference<>() {});
    }

    private List<InterviewQuestionDTO> hydrateQuestions(List<InterviewQuestionDTO> questions, List<InterviewAnswerEntity> answers) {
        List<InterviewQuestionDTO> hydratedQuestions = new java.util.ArrayList<>(questions);
        for (InterviewAnswerEntity answer : answers) {
            int index = answer.getQuestionIndex();
            if (index < 0 || index >= hydratedQuestions.size()) {
                continue;
            }
            InterviewQuestionDTO question = hydratedQuestions.get(index).withAnswer(answer.getUserAnswer());
            if (answer.getScore() != null || answer.getFeedback() != null || answer.getReferenceAnswer() != null) {
                List<String> keyPoints = parseKeyPoints(answer.getKeyPointsJson());
                question = question.withEvaluation(
                    answer.getScore() != null ? answer.getScore() : 0,
                    answer.getFeedback(),
                    answer.getReferenceAnswer(),
                    keyPoints
                );
            }
            hydratedQuestions.set(index, question);
        }
        return hydratedQuestions;
    }

    private List<String> parseKeyPoints(String keyPointsJson) {
        if (keyPointsJson == null || keyPointsJson.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(keyPointsJson, new TypeReference<>() {});
    }

}
