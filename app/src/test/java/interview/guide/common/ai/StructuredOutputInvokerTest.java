package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredOutputInvokerTest {

    private record SamplePayload(String answer, int score) {
    }

    @Test
    void invokeRetriesAndReturnsParsedResult() {
        StructuredOutputInvoker invoker = new StructuredOutputInvoker(2, true);
        AtomicInteger attempts = new AtomicInteger();
        AiTextClient aiTextClient = new StubAiTextClient(() -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt == 1) {
                return "not-json";
            }
            return """
                {"answer":"ok","score":88}
                """;
        });

        SamplePayload payload = invoker.invoke(
            aiTextClient,
            "system",
            "user",
            new BeanOutputConverter<>(SamplePayload.class),
            ErrorCode.RESUME_ANALYSIS_FAILED,
            "结构化失败：",
            "测试调用",
            LoggerFactory.getLogger(StructuredOutputInvokerTest.class)
        );

        assertEquals(2, attempts.get());
        assertEquals("ok", payload.answer());
        assertEquals(88, payload.score());
    }

    @Test
    void invokeThrowsBusinessExceptionWhenRetriesExhausted() {
        StructuredOutputInvoker invoker = new StructuredOutputInvoker(2, true);
        AiTextClient aiTextClient = new StubAiTextClient(() -> "still-not-json");

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> invoker.invoke(
                aiTextClient,
                "system",
                "user",
                new BeanOutputConverter<>(SamplePayload.class),
                ErrorCode.RESUME_ANALYSIS_FAILED,
                "结构化失败：",
                "测试调用",
                LoggerFactory.getLogger(StructuredOutputInvokerTest.class)
            )
        );

        assertEquals(ErrorCode.RESUME_ANALYSIS_FAILED.getCode(), exception.getCode());
    }

    private static final class StubAiTextClient implements AiTextClient {

        private final java.util.function.Supplier<String> responseSupplier;

        private StubAiTextClient(java.util.function.Supplier<String> responseSupplier) {
            this.responseSupplier = responseSupplier;
        }

        @Override
        public String generateText(String systemPrompt, String userPrompt) {
            return responseSupplier.get();
        }

        @Override
        public Flux<String> streamText(String systemPrompt, String userPrompt) {
            return Flux.empty();
        }
    }
}
