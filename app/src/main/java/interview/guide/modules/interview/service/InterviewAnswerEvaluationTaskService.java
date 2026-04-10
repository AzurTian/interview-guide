package interview.guide.modules.interview.service;

import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.modules.interview.config.InterviewAsyncConfig;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 面试答案评估任务服务
 */
@Slf4j
@Service
public class InterviewAnswerEvaluationTaskService {

    private final AnswerEvaluationService evaluationService;
    private final InterviewSessionCache sessionCache;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final Executor answerEvaluationExecutor;

    public InterviewAnswerEvaluationTaskService(
        AnswerEvaluationService evaluationService,
        InterviewSessionCache sessionCache,
        InterviewPersistenceService persistenceService,
        ObjectMapper objectMapper,
        @Qualifier(InterviewAsyncConfig.ANSWER_EVALUATION_EXECUTOR) Executor answerEvaluationExecutor
    ) {
        this.evaluationService = evaluationService;
        this.sessionCache = sessionCache;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
        this.answerEvaluationExecutor = answerEvaluationExecutor;
    }

    public void evaluateAnswerAsync(String sessionId, String resumeText, int questionIndex, String expectedAnswer) {
        answerEvaluationExecutor.execute(() -> evaluateAndPersist(sessionId, resumeText, questionIndex, expectedAnswer));
    }

    public List<InterviewQuestionDTO> evaluateMissingQuestions(String sessionId, String resumeText, List<InterviewQuestionDTO> questions) {
        List<InterviewQuestionDTO> evaluatedQuestions = new ArrayList<>(questions);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int index = 0; index < questions.size(); index++) {
            InterviewQuestionDTO question = questions.get(index);
            if (evaluationService.hasEvaluation(question)) {
                continue;
            }

            int targetIndex = index;
            futures.add(CompletableFuture
                .supplyAsync(() -> evaluationService.evaluateQuestion(sessionId, resumeText, question), answerEvaluationExecutor)
                .thenAccept(evaluatedQuestion -> evaluatedQuestions.set(targetIndex, evaluatedQuestion)));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return evaluatedQuestions;
    }

    private void evaluateAndPersist(String sessionId, String resumeText, int questionIndex, String expectedAnswer) {
        try {
            Optional<CachedSession> sessionOpt = sessionCache.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("异步单题评估跳过，缓存会话不存在: sessionId={}, questionIndex={}", sessionId, questionIndex);
                return;
            }

            CachedSession session = sessionOpt.get();
            List<InterviewQuestionDTO> questions = new ArrayList<>(session.getQuestions(objectMapper));
            if (questionIndex < 0 || questionIndex >= questions.size()) {
                log.warn("异步单题评估跳过，题目索引无效: sessionId={}, questionIndex={}", sessionId, questionIndex);
                return;
            }

            InterviewQuestionDTO currentQuestion = questions.get(questionIndex);
            if (!Objects.equals(currentQuestion.userAnswer(), expectedAnswer)) {
                log.info("异步单题评估检测到答案已变更，跳过旧任务: sessionId={}, questionIndex={}", sessionId, questionIndex);
                return;
            }
            log.debug("异步单题评估开始: sessionId={}, questionIndex={}", sessionId, questionIndex);
            InterviewQuestionDTO evaluatedQuestion = evaluationService.evaluateQuestion(sessionId, resumeText, currentQuestion);

            CachedSession latestSession = sessionCache.getSession(sessionId).orElse(null);
            if (latestSession == null) {
                return;
            }
            List<InterviewQuestionDTO> latestQuestions = new ArrayList<>(latestSession.getQuestions(objectMapper));
            if (questionIndex >= latestQuestions.size()) {
                return;
            }
            InterviewQuestionDTO latestQuestion = latestQuestions.get(questionIndex);
            if (!Objects.equals(latestQuestion.userAnswer(), expectedAnswer)) {
                log.info("异步单题评估写回前检测到答案已变更，丢弃结果: sessionId={}, questionIndex={}", sessionId, questionIndex);
                return;
            }

            latestQuestions.set(questionIndex, evaluatedQuestion);
            sessionCache.updateQuestions(sessionId, latestQuestions);
            persistenceService.updateQuestions(sessionId, latestQuestions);
            persistenceService.updateAnswerEvaluation(
                sessionId,
                questionIndex,
                evaluatedQuestion.question(),
                evaluatedQuestion.category(),
                evaluatedQuestion.userAnswer(),
                evaluatedQuestion.score() != null ? evaluatedQuestion.score() : 0,
                evaluatedQuestion.feedback(),
                evaluatedQuestion.referenceAnswer(),
                evaluatedQuestion.keyPoints()
            );

            log.info("异步单题评估完成: sessionId={}, questionIndex={}, score={}",
                sessionId, questionIndex, evaluatedQuestion.score());
        } catch (Exception exception) {
            log.error("异步单题评估失败: sessionId={}, questionIndex={}", sessionId, questionIndex, exception);
        }
    }
}
