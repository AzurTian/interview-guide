package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.dto.VoiceEvaluationStatusDTO;
import interview.guide.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionStatus;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("语音面试重新评估服务测试")
class VoiceInterviewReevaluationServiceTest {

    private final VoiceInterviewSessionRepository sessionRepository =
        mock(VoiceInterviewSessionRepository.class);
    private final VoiceEvaluateStreamProducer producer = mock(VoiceEvaluateStreamProducer.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RBucket<VoiceInterviewSessionEntity> bucket = mockSessionBucket();
    private final VoiceInterviewService service = new VoiceInterviewService(
        sessionRepository,
        mock(VoiceInterviewMessageRepository.class),
        mock(VoiceInterviewEvaluationRepository.class),
        redissonClient,
        mock(VoiceInterviewProperties.class),
        producer,
        mock(LlmProviderRegistry.class)
    );

    @Nested
    @DisplayName("重新评估")
    class ReevaluateSession {

        @Test
        @DisplayName("已完成会话会更新状态并入队")
        void shouldQueueReevaluationForCompletedSession() {
            VoiceInterviewSessionEntity session = sessionWith(
                VoiceInterviewSessionStatus.COMPLETED,
                AsyncTaskStatus.COMPLETED
            );
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(redissonClient.<VoiceInterviewSessionEntity>getBucket(anyString())).thenReturn(bucket);

            VoiceEvaluationStatusDTO result = service.reevaluateSession(1L);

            assertThat(result.getEvaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING.name());
            assertThat(result.getEvaluateError()).isNull();
            assertThat(session.getEvaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING);
            assertThat(session.getEvaluateError()).isNull();
            verify(sessionRepository).save(session);
            verify(producer).sendEvaluateTask("1");
            verify(bucket).delete();
        }

        @Test
        @DisplayName("评估已在队列中时不重复入队")
        void shouldReturnCurrentStatusWhenPending() {
            VoiceInterviewSessionEntity session = sessionWith(
                VoiceInterviewSessionStatus.COMPLETED,
                AsyncTaskStatus.PENDING
            );
            session.setEvaluateError("waiting");
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            VoiceEvaluationStatusDTO result = service.reevaluateSession(1L);

            assertThat(result.getEvaluateStatus()).isEqualTo(AsyncTaskStatus.PENDING.name());
            assertThat(result.getEvaluateError()).isEqualTo("waiting");
            verify(sessionRepository, never()).save(session);
            verify(producer, never()).sendEvaluateTask("1");
        }

        @Test
        @DisplayName("未完成会话拒绝重新评估")
        void shouldRejectIncompleteSession() {
            VoiceInterviewSessionEntity session = sessionWith(
                VoiceInterviewSessionStatus.IN_PROGRESS,
                AsyncTaskStatus.FAILED
            );
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.reevaluateSession(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("语音面试尚未完成");

            verify(producer, never()).sendEvaluateTask("1");
        }
    }

    private VoiceInterviewSessionEntity sessionWith(
        VoiceInterviewSessionStatus status,
        AsyncTaskStatus evaluateStatus
    ) {
        return VoiceInterviewSessionEntity.builder()
            .id(1L)
            .status(status)
            .evaluateStatus(evaluateStatus)
            .evaluateError("previous")
            .build();
    }

    @SuppressWarnings("unchecked")
    private RBucket<VoiceInterviewSessionEntity> mockSessionBucket() {
        return mock(RBucket.class);
    }
}
