package interview.guide.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Text-only HTTP ChatModel adapters")
class TextOnlyChatModelAdapterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("OpenAI Responses request maps system prompt to instructions")
  void openAiResponsesBuildRequest() {
    OpenAiResponsesChatModel model = new OpenAiResponsesChatModel(
        "https://api.openai.com/v1", "key", "gpt-test", 0.3, objectMapper);

    Map<String, Object> request = model.buildRequest(new Prompt(List.of(
        new SystemMessage("system"),
        new UserMessage("hello"),
        new AssistantMessage("previous")
    )), true);

    assertThat(request)
        .containsEntry("model", "gpt-test")
        .containsEntry("instructions", "system")
        .containsEntry("temperature", 0.3)
        .containsEntry("stream", true);
    assertThat(request.get("input")).asList().hasSize(2);
  }

  @Test
  @DisplayName("OpenAI Responses stream parser extracts output text deltas")
  void openAiResponsesExtractStreamDelta() throws IOException {
    OpenAiResponsesChatModel model = new OpenAiResponsesChatModel(
        "https://api.openai.com/v1", "key", "gpt-test", null, objectMapper);

    String delta = model.extractStreamDelta(
        "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}");

    assertThat(delta).isEqualTo("hello");
    assertThat(model.extractStreamDelta("data: [DONE]")).isNull();
  }

  @Test
  @DisplayName("Anthropic Messages request maps system prompt separately")
  void anthropicBuildRequest() {
    AnthropicMessagesChatModel model = new AnthropicMessagesChatModel(
        "https://api.anthropic.com", "key", "claude-test", 0.2, objectMapper);

    Map<String, Object> request = model.buildRequest(new Prompt(List.of(
        new SystemMessage("system"),
        new UserMessage("hello"),
        new AssistantMessage("previous")
    )), true);

    assertThat(request)
        .containsEntry("model", "claude-test")
        .containsEntry("system", "system")
        .containsEntry("temperature", 0.2)
        .containsEntry("stream", true)
        .containsEntry("max_tokens", 1024);
    assertThat(request.get("messages")).asList().hasSize(2);
  }

  @Test
  @DisplayName("Anthropic Messages stream parser extracts text deltas")
  void anthropicExtractStreamDelta() throws IOException {
    AnthropicMessagesChatModel model = new AnthropicMessagesChatModel(
        "https://api.anthropic.com", "key", "claude-test", null, objectMapper);

    String delta = model.extractStreamDelta(
        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}");

    assertThat(delta).isEqualTo("hello");
    assertThat(model.extractStreamDelta("event: message_stop")).isNull();
  }
}
