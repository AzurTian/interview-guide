package interview.guide.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

abstract class AbstractHttpTextChatModel implements ChatModel {

  protected static final int DEFAULT_CONNECT_TIMEOUT = 10000;
  protected static final int DEFAULT_READ_TIMEOUT = 300000;

  protected final String baseUrl;
  protected final String apiKey;
  protected final String model;
  protected final Double temperature;
  protected final ObjectMapper objectMapper;
  protected final RestClient restClient;
  private final ChatOptions defaultOptions;

  protected AbstractHttpTextChatModel(
      String baseUrl,
      String apiKey,
      String model,
      Double temperature,
      ObjectMapper objectMapper) {
    this.baseUrl = ApiPathResolver.stripTrailingSlashes(baseUrl);
    this.apiKey = ApiKeySanitizer.requirePresent(apiKey, model);
    this.model = model;
    this.temperature = temperature;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(DEFAULT_READ_TIMEOUT);
    this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .build();
    this.defaultOptions = ChatOptions.builder()
        .model(model)
        .temperature(temperature)
        .build();
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    try {
      String body = restClient.post()
          .uri(endpointUrl())
          .headers(headers -> headers(headers, false))
          .contentType(MediaType.APPLICATION_JSON)
          .body(buildRequest(prompt, false))
          .retrieve()
          .body(String.class);
      return toChatResponse(extractText(objectMapper.readTree(body)), Map.of("model", model));
    } catch (RestClientResponseException e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
          providerName() + " 调用失败: endpoint=" + endpointUrl()
              + ", HTTP " + e.getStatusCode().value()
              + ", body=" + abbreviate(e.getResponseBodyAsString()));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
          providerName() + " 调用失败: " + e.getMessage());
    }
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.<ChatResponse>create(sink -> {
      try {
        restClient.post()
            .uri(endpointUrl())
            .headers(headers -> headers(headers, true))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .body(buildRequest(prompt, true))
            .exchange((request, response) -> {
              HttpStatusCode status = response.getStatusCode();
              if (!status.is2xxSuccessful()) {
                String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                    providerName() + " 流式调用失败: HTTP " + status.value() + " " + responseBody);
              }
              try (BufferedReader reader = new BufferedReader(
                  new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while (!sink.isCancelled() && (line = reader.readLine()) != null) {
                  String delta = extractStreamDelta(line);
                  if (delta != null && !delta.isEmpty()) {
                    sink.next(toChatResponse(delta, Map.of("model", model, "stream", true)));
                  }
                }
              }
              return null;
            });
        if (!sink.isCancelled()) {
          sink.complete();
        }
      } catch (BusinessException e) {
        sink.error(e);
      } catch (Exception e) {
        sink.error(new BusinessException(ErrorCode.AI_SERVICE_ERROR,
            providerName() + " 流式调用失败: " + e.getMessage()));
      }
    }).subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return defaultOptions;
  }

  protected ChatResponse toChatResponse(String text, Map<String, Object> metadata) {
    AssistantMessage message = AssistantMessage.builder()
        .content(text != null ? text : "")
        .properties(metadata)
        .build();
    return ChatResponse.builder()
        .metadata(ChatResponseMetadata.builder()
            .model(model)
            .build())
        .generations(List.of(new Generation(message)))
        .build();
  }

  protected String dataPayload(String line) {
    if (line == null) {
      return null;
    }
    String trimmed = line.trim();
    if (!trimmed.startsWith("data:")) {
      return null;
    }
    String data = trimmed.substring("data:".length()).trim();
    if (data.isBlank() || "[DONE]".equals(data)) {
      return null;
    }
    return data;
  }

  protected String text(Message message) {
    return message.getText() != null ? message.getText() : "";
  }

  private String abbreviate(String text) {
    if (text == null || text.isBlank()) {
      return "[no body]";
    }
    String normalized = text.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= 200) {
      return normalized;
    }
    return normalized.substring(0, 200) + "...";
  }

  protected abstract String providerName();

  protected abstract String endpointUrl();

  protected abstract void headers(org.springframework.http.HttpHeaders headers, boolean stream);

  protected abstract Map<String, Object> buildRequest(Prompt prompt, boolean stream);

  protected abstract String extractText(JsonNode response);

  protected abstract String extractStreamDelta(String line) throws IOException;
}
