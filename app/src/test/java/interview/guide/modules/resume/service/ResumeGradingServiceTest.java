package interview.guide.modules.resume.service;

import interview.guide.common.ai.AiTextClient;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeGradingServiceTest {

    @Test
    void analyzeResumeParsesResponseFromAiTextClient() throws Exception {
        AiTextClient aiTextClient = new FixedAiTextClient("""
            {
              "overallScore": 86,
              "scoreDetail": {
                "contentScore": 14,
                "structureScore": 16,
                "skillMatchScore": 18,
                "expressionScore": 17,
                "projectScore": 21
              },
              "summary": "整体较好",
              "strengths": ["项目描述清晰", "技术栈完整"],
              "suggestions": [
                {
                  "category": "项目经历",
                  "priority": "高",
                  "issue": "量化指标不足",
                  "recommendation": "补充性能和业务结果指标"
                }
              ]
            }
            """);
        ResumeGradingService service = new ResumeGradingService(
            aiTextClient,
            new StructuredOutputInvoker(2, true),
            new ClassPathResource("prompts/resume-analysis-system.st"),
            new ClassPathResource("prompts/resume-analysis-user.st")
        );

        ResumeAnalysisResponse response = service.analyzeResume("这是简历正文");

        assertEquals(86, response.overallScore());
        assertEquals("整体较好", response.summary());
        assertEquals(2, response.strengths().size());
        assertEquals(1, response.suggestions().size());
        assertEquals("项目经历", response.suggestions().getFirst().category());
    }

    private static final class FixedAiTextClient implements AiTextClient {

        private final String responseText;

        private FixedAiTextClient(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public String generateText(String systemPrompt, String userPrompt) {
            return responseText;
        }

        @Override
        public Flux<String> streamText(String systemPrompt, String userPrompt) {
            return Flux.just(responseText);
        }
    }
}
