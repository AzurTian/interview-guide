package interview.guide.modules.knowledgebase.service;

import interview.guide.common.ai.AiTextClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseQueryServiceTest {

    @Mock
    private KnowledgeBaseVectorService vectorService;

    @Mock
    private KnowledgeBaseListService listService;

    @Mock
    private KnowledgeBaseCountService countService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void answerQuestionStreamUsesAiTextClientStream() throws Exception {
        AiTextClient aiTextClient = new StubAiTextClient();
        when(vectorService.similaritySearch(anyString(), anyList(), anyInt(), anyDouble()))
            .thenReturn(List.of(new Document("Java 是一种语言，也是一套生态。")));

        KnowledgeBaseQueryService service = new KnowledgeBaseQueryService(
            aiTextClient,
            vectorService,
            listService,
            countService,
            new ClassPathResource("prompts/knowledgebase-query-system.st"),
            new ClassPathResource("prompts/knowledgebase-query-user.st"),
            new ClassPathResource("prompts/knowledgebase-query-rewrite.st"),
            false,
            4,
            20,
            12,
            8,
            0.18,
            0.28
        );

        List<String> chunks = service.answerQuestionStream(List.of(1L), "Java").collectList().block();

        assertEquals(List.of("第一段第二段"), chunks);
        verify(countService, times(1)).updateQuestionCounts(List.of(1L));
    }

    private static final class StubAiTextClient implements AiTextClient {

        @Override
        public String generateText(String systemPrompt, String userPrompt) {
            return "完整回答";
        }

        @Override
        public Flux<String> streamText(String systemPrompt, String userPrompt) {
            return Flux.just("第一段", "第二段");
        }
    }
}
