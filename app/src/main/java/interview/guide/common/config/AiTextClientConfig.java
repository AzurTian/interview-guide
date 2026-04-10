package interview.guide.common.config;

import interview.guide.common.ai.AiTextClient;
import interview.guide.common.ai.ResponsesApiTextClient;
import interview.guide.common.ai.SpringAiTextClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 文本生成客户端装配。
 */
@Configuration
public class AiTextClientConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.ai", name = "text-provider", havingValue = "spring-ai", matchIfMissing = true)
    public AiTextClient springAiTextClient(ChatClient.Builder chatClientBuilder) {
        return new SpringAiTextClient(chatClientBuilder);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.ai", name = "text-provider", havingValue = "responses")
    public AiTextClient responsesApiTextClient(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        AiTextConfigProperties properties
    ) {
        return new ResponsesApiTextClient(webClientBuilder, objectMapper, properties);
    }
}
