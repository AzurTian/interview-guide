package interview.guide.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnthropicMessagesChatModel extends AbstractHttpTextChatModel {

  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final int DEFAULT_MAX_TOKENS = 1024;

  public AnthropicMessagesChatModel(
      String baseUrl,
      String apiKey,
      String model,
      Double temperature,
      ObjectMapper objectMapper) {
    super(baseUrl, apiKey, model, temperature, objectMapper);
  }

  @Override
  protected String providerName() {
    return "Anthropic Messages";
  }

  @Override
  protected String endpointUrl() {
    if (baseUrl.endsWith("/messages")) {
      return baseUrl;
    }
    if (baseUrl.endsWith("/v1")) {
      return baseUrl + "/messages";
    }
    return baseUrl + "/v1/messages";
  }

  @Override
  protected void headers(HttpHeaders headers, boolean stream) {
    headers.set("x-api-key", apiKey);
    headers.set("anthropic-version", ANTHROPIC_VERSION);
  }

  @Override
  protected Map<String, Object> buildRequest(Prompt prompt, boolean stream) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", model);
    request.put("max_tokens", DEFAULT_MAX_TOKENS);
    String system = buildSystem(prompt);
    if (!system.isBlank()) {
      request.put("system", system);
    }
    request.put("messages", buildMessages(prompt));
    if (temperature != null) {
      request.put("temperature", temperature);
    }
    if (stream) {
      request.put("stream", true);
    }
    return request;
  }

  @Override
  protected String extractText(JsonNode response) {
    StringBuilder text = new StringBuilder();
    JsonNode content = response.get("content");
    if (content != null && content.isArray()) {
      for (JsonNode part : content) {
        JsonNode type = part.get("type");
        JsonNode partText = part.get("text");
        if (partText != null && partText.isTextual()
            && (type == null || "text".equals(type.asText()))) {
          text.append(partText.asText());
        }
      }
    }
    return text.toString();
  }

  @Override
  protected String extractStreamDelta(String line) throws IOException {
    String data = dataPayload(line);
    if (data == null) {
      return null;
    }
    JsonNode event = objectMapper.readTree(data);
    JsonNode type = event.get("type");
    if (type == null || !"content_block_delta".equals(type.asText())) {
      return null;
    }
    JsonNode delta = event.get("delta");
    if (delta == null) {
      return null;
    }
    JsonNode deltaType = delta.get("type");
    JsonNode text = delta.get("text");
    if (text != null && text.isTextual()
        && (deltaType == null || "text_delta".equals(deltaType.asText()))) {
      return text.asText();
    }
    return null;
  }

  private String buildSystem(Prompt prompt) {
    return prompt.getInstructions().stream()
        .filter(message -> message.getMessageType() == MessageType.SYSTEM)
        .map(this::text)
        .filter(value -> !value.isBlank())
        .reduce((left, right) -> left + "\n\n" + right)
        .orElse("");
  }

  private List<Map<String, Object>> buildMessages(Prompt prompt) {
    List<Map<String, Object>> messages = new ArrayList<>();
    for (Message message : prompt.getInstructions()) {
      if (message.getMessageType() == MessageType.SYSTEM) {
        continue;
      }
      String role = message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user";
      messages.add(Map.of("role", role, "content", text(message)));
    }
    if (messages.isEmpty()) {
      messages.add(Map.of("role", "user", "content", ""));
    }
    return messages;
  }
}
