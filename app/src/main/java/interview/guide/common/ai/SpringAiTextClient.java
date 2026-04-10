package interview.guide.common.ai;

import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI ChatClient 的文本客户端实现。
 */
public class SpringAiTextClient implements AiTextClient {

    private final ChatClient chatClient;

    public SpringAiTextClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return applyPrompts(chatClient.prompt(), systemPrompt, userPrompt)
            .call()
            .content();
    }

    @Override
    public Flux<String> streamText(String systemPrompt, String userPrompt) {
        return Flux.defer(() -> applyPrompts(chatClient.prompt(), systemPrompt, userPrompt)
            .stream()
            .content());
    }

    private ChatClient.ChatClientRequestSpec applyPrompts(
        ChatClient.ChatClientRequestSpec promptSpec,
        String systemPrompt,
        String userPrompt
    ) {
        ChatClient.ChatClientRequestSpec requestSpec = promptSpec;
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestSpec = requestSpec.system(systemPrompt);
        }
        return requestSpec.user(userPrompt != null ? userPrompt : "");
    }
}
