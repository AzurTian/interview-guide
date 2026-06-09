package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.evaluation.EvaluationReport;
import interview.guide.common.evaluation.UnifiedEvaluationService;
import interview.guide.modules.interview.skill.InterviewSkillService;
import interview.guide.modules.voiceinterview.model.VoiceInterviewEvaluationEntity;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("语音面试评估保存测试")
class VoiceInterviewEvaluationServiceTest {

    private final VoiceInterviewEvaluationRepository evaluationRepository =
        mock(VoiceInterviewEvaluationRepository.class);
    private final VoiceInterviewEvaluationService service = new VoiceInterviewEvaluationService(
        mock(UnifiedEvaluationService.class),
        mock(LlmProviderRegistry.class),
        evaluationRepository,
        mock(VoiceInterviewMessageRepository.class),
        mock(VoiceInterviewSessionRepository.class),
        new ObjectMapper(),
        mock(InterviewSkillService.class)
    );

    @Test
    @DisplayName("保存评估时更新已有记录")
    void shouldUpdateExistingEvaluationWhenPresent() {
        VoiceInterviewEvaluationEntity existing = VoiceInterviewEvaluationEntity.builder()
            .id(10L)
            .sessionId(1L)
            .overallScore(60)
            .overallFeedback("旧反馈")
            .build();
        VoiceInterviewSessionEntity session = VoiceInterviewSessionEntity.builder()
            .id(1L)
            .roleType("java-backend")
            .startTime(LocalDateTime.now())
            .build();
        EvaluationReport report = new EvaluationReport(
            "1",
            1,
            88,
            List.of(new EvaluationReport.CategoryScore("技术问题", 88, 1)),
            List.of(new EvaluationReport.QuestionEvaluation(
                0,
                "问题",
                "技术问题",
                "回答",
                88,
                "新反馈"
            )),
            "总体反馈",
            List.of("优势"),
            List.of("改进"),
            List.of(new EvaluationReport.ReferenceAnswer(
                0,
                "问题",
                "参考答案",
                List.of("关键点")
            ))
        );
        when(evaluationRepository.findBySessionId(1L)).thenReturn(Optional.of(existing));

        service.saveEvaluationTransactional(1L, session, report);

        ArgumentCaptor<VoiceInterviewEvaluationEntity> captor =
            ArgumentCaptor.forClass(VoiceInterviewEvaluationEntity.class);
        verify(evaluationRepository).save(captor.capture());
        VoiceInterviewEvaluationEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getSessionId()).isEqualTo(1L);
        assertThat(saved.getOverallScore()).isEqualTo(88);
        assertThat(saved.getOverallFeedback()).isEqualTo("总体反馈");
        assertThat(saved.getQuestionEvaluationsJson()).contains("新反馈");
        assertThat(saved.getStrengthsJson()).contains("优势");
        assertThat(saved.getImprovementsJson()).contains("改进");
        assertThat(saved.getReferenceAnswersJson()).contains("参考答案");
    }
}
