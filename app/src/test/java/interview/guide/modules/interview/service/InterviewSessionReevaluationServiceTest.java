package interview.guide.modules.interview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.InterviewEvaluationStatusDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("文字面试重新评估服务测试")
class InterviewSessionReevaluationServiceTest {

    private final InterviewPersistenceService persistenceService = mock(InterviewPersistenceService.class);
    private final EvaluateStreamProducer evaluateStreamProducer = mock(EvaluateStreamProducer.class);
    private final InterviewSessionService service = new InterviewSessionService(
        mock(InterviewQuestionService.class),
        mock(AnswerEvaluationService.class),
        persistenceService,
        mock(InterviewSessionCache.class),
        mock(ObjectMapper.class),
        evaluateStreamProducer,
        mock(LlmProviderRegistry.class)
    );

    @Nested
    @DisplayName("重新评估")
    class ReevaluateInterview {

        @Test
        @DisplayName("已完成会话会更新状态并入队")
        void shouldQueueReevaluationForCompletedSession() {
            InterviewSessionEntity session = sessionWith(
                InterviewSessionEntity.SessionStatus.EVALUATED,
                AsyncTaskStatus.COMPLETED
            );
            when(persistenceService.findBySessionId("s1")).thenReturn(Optional.of(session));

            InterviewEvaluationStatusDTO result = service.reevaluateInterview("s1");

            assertThat(result.evaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING.name());
            assertThat(result.evaluateError()).isNull();
            verify(persistenceService).updateEvaluateStatus("s1", AsyncTaskStatus.PENDING, null);
            verify(evaluateStreamProducer).sendEvaluateTask("s1");
        }

        @Test
        @DisplayName("评估已在队列中时不重复入队")
        void shouldReturnCurrentStatusWhenPending() {
            InterviewSessionEntity session = sessionWith(
                InterviewSessionEntity.SessionStatus.EVALUATED,
                AsyncTaskStatus.PENDING
            );
            session.setEvaluateError("waiting");
            when(persistenceService.findBySessionId("s1")).thenReturn(Optional.of(session));

            InterviewEvaluationStatusDTO result = service.reevaluateInterview("s1");

            assertThat(result.evaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING.name());
            assertThat(result.evaluateError()).isEqualTo("waiting");
            verify(persistenceService, never()).updateEvaluateStatus("s1", AsyncTaskStatus.PENDING, null);
            verify(evaluateStreamProducer, never()).sendEvaluateTask("s1");
        }

        @Test
        @DisplayName("未完成会话拒绝重新评估")
        void shouldRejectIncompleteSession() {
            InterviewSessionEntity session = sessionWith(
                InterviewSessionEntity.SessionStatus.IN_PROGRESS,
                AsyncTaskStatus.FAILED
            );
            when(persistenceService.findBySessionId("s1")).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.reevaluateInterview("s1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("面试尚未完成");

            verify(evaluateStreamProducer, never()).sendEvaluateTask("s1");
        }
    }

    private InterviewSessionEntity sessionWith(
        InterviewSessionEntity.SessionStatus status,
        AsyncTaskStatus evaluateStatus
    ) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setSessionId("s1");
        session.setStatus(status);
        session.setEvaluateStatus(evaluateStatus);
        return session;
    }
}
