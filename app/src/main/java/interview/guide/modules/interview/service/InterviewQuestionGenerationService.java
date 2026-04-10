package interview.guide.modules.interview.service;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.modules.interview.config.InterviewAsyncConfig;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.service.InterviewQuestionService.GeneratedQuestionSlot;
import interview.guide.modules.interview.service.InterviewQuestionService.QuestionSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 面试题目异步生成协调服务
 */
@Slf4j
@Service
public class InterviewQuestionGenerationService {

    private final InterviewQuestionService questionService;
    private final InterviewSessionCache sessionCache;
    private final InterviewPersistenceService persistenceService;
    private final InterviewQuestionStreamService questionStreamService;
    private final ObjectMapper objectMapper;
    private final Executor questionGenerationExecutor;

    public InterviewQuestionGenerationService(
        InterviewQuestionService questionService,
        InterviewSessionCache sessionCache,
        InterviewPersistenceService persistenceService,
        InterviewQuestionStreamService questionStreamService,
        ObjectMapper objectMapper,
        @Qualifier(InterviewAsyncConfig.QUESTION_GENERATION_EXECUTOR) Executor questionGenerationExecutor
    ) {
        this.questionService = questionService;
        this.sessionCache = sessionCache;
        this.persistenceService = persistenceService;
        this.questionStreamService = questionStreamService;
        this.objectMapper = objectMapper;
        this.questionGenerationExecutor = questionGenerationExecutor;
    }

    public void startGeneration(
        String sessionId,
        String resumeText,
        int questionCount,
        List<String> historicalQuestions,
        int totalQuestions
    ) {
        questionGenerationExecutor.execute(() -> generateQuestions(sessionId, resumeText, questionCount, historicalQuestions, totalQuestions));
    }

    private void generateQuestions(
        String sessionId,
        String resumeText,
        int questionCount,
        List<String> historicalQuestions,
        int totalQuestions
    ) {
        log.info("开始异步生成面试题目: sessionId={}, requestedQuestionCount={}, totalQuestions={}",
            sessionId, questionCount, totalQuestions);

        updateQuestionGenerationState(sessionId, AsyncTaskStatus.PROCESSING, null);

        List<QuestionSlot> slots = questionService.buildQuestionSlots(questionCount);
        Map<Integer, InterviewQuestionDTO> questionMap = new ConcurrentHashMap<>();
        Set<String> generatedMainQuestions = ConcurrentHashMap.newKeySet();
        Object stateLock = new Object();

        try {
            List<CompletableFuture<Void>> futures = slots.stream()
                .map(slot -> CompletableFuture
                    .supplyAsync(
                        () -> questionService.generateQuestionSlot(
                            resumeText,
                            slot,
                            historicalQuestions,
                            generatedMainQuestions
                        ),
                        questionGenerationExecutor
                    )
                    .thenAccept(generatedSlot -> applyGeneratedSlot(
                        sessionId,
                        generatedSlot,
                        questionMap,
                        totalQuestions,
                        stateLock
                    )))
                .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            updateQuestionGenerationState(sessionId, AsyncTaskStatus.COMPLETED, null);
            questionStreamService.publishCompleted(sessionId, totalQuestions, totalQuestions);
            log.info("异步题目生成完成: sessionId={}, totalQuestions={}", sessionId, totalQuestions);
        } catch (Exception exception) {
            String error = "题目生成失败：" + exception.getMessage();
            updateQuestionGenerationState(sessionId, AsyncTaskStatus.FAILED, error);
            int generatedQuestionCount = currentGeneratedQuestionCount(sessionId);
            questionStreamService.publishError(sessionId, error, generatedQuestionCount, totalQuestions);
            log.error("异步题目生成失败: sessionId={}", sessionId, exception);
        }
    }

    private void applyGeneratedSlot(
        String sessionId,
        GeneratedQuestionSlot generatedSlot,
        Map<Integer, InterviewQuestionDTO> questionMap,
        int totalQuestions,
        Object stateLock
    ) {
        synchronized (stateLock) {
            for (InterviewQuestionDTO question : generatedSlot.questions()) {
                questionMap.put(question.questionIndex(), question);
            }

            List<InterviewQuestionDTO> orderedQuestions = new ArrayList<>(questionMap.values());
            orderedQuestions.sort(Comparator.comparingInt(InterviewQuestionDTO::questionIndex));

            sessionCache.updateQuestions(sessionId, orderedQuestions);
            persistenceService.updateQuestions(sessionId, orderedQuestions);

            InterviewSessionDTO sessionSnapshot = currentSessionSnapshot(sessionId);
            int generatedQuestionCount = orderedQuestions.size();
            for (InterviewQuestionDTO question : generatedSlot.questions()) {
                questionStreamService.publishQuestion(
                    sessionId,
                    question,
                    generatedQuestionCount,
                    totalQuestions,
                    sessionSnapshot
                );
            }

            log.info("题目槽位已生成: sessionId={}, slotOrder={}, generatedQuestionCount={}/{}",
                sessionId, generatedSlot.slot().slotOrder(), generatedQuestionCount, totalQuestions);
        }
    }

    private void updateQuestionGenerationState(String sessionId, AsyncTaskStatus status, String error) {
        sessionCache.updateQuestionGenerationState(sessionId, status, error);
        persistenceService.updateQuestionGenerationState(sessionId, status, error);
    }

    private InterviewSessionDTO currentSessionSnapshot(String sessionId) {
        CachedSession cachedSession = sessionCache.getSession(sessionId)
            .orElseThrow(() -> new IllegalStateException("会话缓存不存在: " + sessionId));
        List<InterviewQuestionDTO> questions = cachedSession.getQuestions(objectMapper);
        return new InterviewSessionDTO(
            cachedSession.getSessionId(),
            cachedSession.getResumeText(),
            cachedSession.getTotalQuestions(),
            cachedSession.getGeneratedQuestionCount(),
            cachedSession.getCurrentIndex(),
            questions,
            cachedSession.getStatus(),
            cachedSession.getQuestionGenerationStatus(),
            cachedSession.getQuestionGenerationError()
        );
    }

    private int currentGeneratedQuestionCount(String sessionId) {
        return sessionCache.getSession(sessionId)
            .map(CachedSession::getGeneratedQuestionCount)
            .orElse(0);
    }
}
