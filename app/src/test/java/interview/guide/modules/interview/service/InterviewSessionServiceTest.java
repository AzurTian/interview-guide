package interview.guide.modules.interview.service;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.CreateInterviewRequest;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import interview.guide.modules.interview.model.SubmitAnswerRequest;
import interview.guide.modules.interview.model.SubmitAnswerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {

    @Mock
    private InterviewQuestionService questionService;

    @Mock
    private AnswerEvaluationService evaluationService;

    @Mock
    private InterviewQuestionGenerationService questionGenerationService;

    @Mock
    private InterviewAnswerEvaluationTaskService answerEvaluationTaskService;

    @Mock
    private InterviewPersistenceService persistenceService;

    @Mock
    private InterviewSessionCache sessionCache;

    @Mock
    private EvaluateStreamProducer evaluateStreamProducer;

    private InterviewSessionService interviewSessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        interviewSessionService = new InterviewSessionService(
            questionService,
            evaluationService,
            questionGenerationService,
            answerEvaluationTaskService,
            persistenceService,
            sessionCache,
            objectMapper,
            evaluateStreamProducer
        );
    }

    @Test
    void createSessionReturnsPendingSessionAndStartsAsyncGeneration() {
        CreateInterviewRequest request = new CreateInterviewRequest(
            "resume text",
            4,
            1L,
            false
        );

        when(persistenceService.getHistoricalQuestionsByResumeId(1L)).thenReturn(List.of("历史题目"));
        when(questionService.calculateTotalQuestionCount(4)).thenReturn(4);

        var session = interviewSessionService.createSession(request);

        assertEquals(4, session.totalQuestions());
        assertEquals(0, session.generatedQuestionCount());
        assertEquals(AsyncTaskStatus.PENDING, session.questionGenerationStatus());
        assertTrue(session.questions().isEmpty());

        verify(sessionCache).saveSession(
            eq(session.sessionId()),
            eq("resume text"),
            eq(1L),
            eq(4),
            eq(List.of()),
            eq(0),
            eq(0),
            eq(SessionStatus.CREATED),
            eq(AsyncTaskStatus.PENDING),
            eq(null)
        );
        verify(questionGenerationService).startGeneration(
            eq(session.sessionId()),
            eq("resume text"),
            eq(4),
            eq(List.of("历史题目")),
            eq(4)
        );
    }

    @Test
    void submitAnswerReturnsWaitingWhenNextQuestionNotGenerated() {
        List<InterviewQuestionDTO> questions = List.of(
            InterviewQuestionDTO.create(0, "请介绍一下你的项目", InterviewQuestionDTO.QuestionType.PROJECT, "项目经历")
        );
        InterviewSessionCache.CachedSession cachedSession = new InterviewSessionCache.CachedSession(
            "session-1",
            "resume text",
            1L,
            4,
            questions,
            1,
            0,
            SessionStatus.IN_PROGRESS,
            AsyncTaskStatus.PROCESSING,
            null,
            objectMapper
        );

        when(sessionCache.getSession("session-1")).thenReturn(Optional.of(cachedSession));

        SubmitAnswerResponse response = interviewSessionService.submitAnswer(
            new SubmitAnswerRequest("session-1", 0, "这是我的回答")
        );

        assertFalse(response.hasNextQuestion());
        assertTrue(response.waitingForNextQuestion());
        assertFalse(response.completed());
        assertEquals(1, response.currentIndex());
        assertEquals(4, response.totalQuestions());
        assertEquals(null, response.nextQuestion());

        ArgumentCaptor<List<InterviewQuestionDTO>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionCache).updateQuestions(eq("session-1"), questionsCaptor.capture());
        InterviewQuestionDTO answeredQuestion = questionsCaptor.getValue().getFirst();
        assertEquals("这是我的回答", answeredQuestion.userAnswer());
        assertNotNull(answeredQuestion);

        verify(answerEvaluationTaskService).evaluateAnswerAsync("session-1", "resume text", 0, "这是我的回答");
        verify(evaluateStreamProducer, never()).sendEvaluateTask(any());
        verify(sessionCache).updateCurrentIndex("session-1", 1);
        verify(sessionCache).updateSessionStatus("session-1", SessionStatus.IN_PROGRESS);
        verify(persistenceService).updateQuestions(eq("session-1"), any());
        verify(persistenceService, never()).updateEvaluateStatus(eq("session-1"), any(), any());
    }

    @Test
    void submitAnswerMarksSessionCompletedWhenLastQuestionIsAnswered() {
        List<InterviewQuestionDTO> questions = List.of(
            InterviewQuestionDTO.create(0, "第一题", InterviewQuestionDTO.QuestionType.PROJECT, "项目经历"),
            InterviewQuestionDTO.create(1, "第二题", InterviewQuestionDTO.QuestionType.MYSQL, "MySQL")
        );
        InterviewSessionCache.CachedSession cachedSession = new InterviewSessionCache.CachedSession(
            "session-2",
            "resume text",
            1L,
            2,
            questions,
            2,
            1,
            SessionStatus.IN_PROGRESS,
            AsyncTaskStatus.COMPLETED,
            null,
            objectMapper
        );

        when(sessionCache.getSession("session-2")).thenReturn(Optional.of(cachedSession));

        SubmitAnswerResponse response = interviewSessionService.submitAnswer(
            new SubmitAnswerRequest("session-2", 1, "最后一题回答")
        );

        assertFalse(response.hasNextQuestion());
        assertFalse(response.waitingForNextQuestion());
        assertTrue(response.completed());
        assertEquals(2, response.currentIndex());
        assertEquals(2, response.totalQuestions());

        verify(answerEvaluationTaskService).evaluateAnswerAsync("session-2", "resume text", 1, "最后一题回答");
        verify(sessionCache).updateCurrentIndex("session-2", 2);
        verify(sessionCache).updateSessionStatus("session-2", SessionStatus.COMPLETED);
        verify(persistenceService).updateEvaluateStatus("session-2", AsyncTaskStatus.PENDING, null);
        verify(evaluateStreamProducer).sendEvaluateTask("session-2");
    }
}
