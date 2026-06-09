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

public class OpenAiResponsesChatModel extends AbstractHttpTextChatModel {

  public OpenAiResponsesChatModel(
      String baseUrl,
      String apiKey,
      String model,
      Double temperature,
      ObjectMapper objectMapper) {
    super(baseUrl, apiKey, model, temperature, objectMapper);
  }

  @Override
  protected String providerName() {
    return "OpenAI Responses";
  }

  @Override
  protected String endpointUrl() {
    if (baseUrl.endsWith("/responses")) {
      return baseUrl;
    }
    return baseUrl + "/responses";
  }

  @Override
  protected void headers(HttpHeaders headers, boolean stream) {
    headers.setBearerAuth(apiKey);
  }

  @Override
  protected Map<String, Object> buildRequest(Prompt prompt, boolean stream) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", model);
    String instructions = buildInstructions(prompt);
    if (!instructions.isBlank()) {
      request.put("instructions", instructions);
    }
    request.put("input", buildInput(prompt));
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
    JsonNode outputText = response.get("output_text");
    if (outputText != null && outputText.isTextual()) {
      return outputText.asText();
    }
    StringBuilder text = new StringBuilder();
    JsonNode output = response.get("output");
    if (output != null && output.isArray()) {
      for (JsonNode item : output) {
        JsonNode content = item.get("content");
        if (content == null || !content.isArray()) {
          continue;
        }
        for (JsonNode part : content) {
          JsonNode partText = part.get("text");
          if (partText != null && partText.isTextual()) {
            text.append(partText.asText());
          }
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
    if (type != null && "response.output_text.delta".equals(type.asText())) {
      JsonNode delta = event.get("delta");
      return delta != null && delta.isTextual() ? delta.asText() : null;
    }
    return null;
  }

  private String buildInstructions(Prompt prompt) {
    return prompt.getInstructions().stream()
        .filter(message -> message.getMessageType() == MessageType.SYSTEM)
        .map(this::text)
        .filter(value -> !value.isBlank())
        .reduce((left, right) -> left + "\n\n" + right)
        .orElse("");
  }

  private List<Map<String, Object>> buildInput(Prompt prompt) {
    List<Map<String, Object>> input = new ArrayList<>();
    for (Message message : prompt.getInstructions()) {
      if (message.getMessageType() == MessageType.SYSTEM) {
        continue;
      }
      String role = message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user";
      input.add(Map.of("role", role, "content", text(message)));
    }
    if (input.isEmpty()) {
      input.add(Map.of("role", "user", "content", ""));
    }
    return input;
  }
}
