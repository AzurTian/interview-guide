package interview.guide.common.ai;

import interview.guide.common.config.AiTextConfigProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 OpenAI Responses API 的文本客户端实现。
 * 供应商只支持流式时，非流式调用也通过聚合流式增量实现。
 */
public class ResponsesApiTextClient implements AiTextClient {

    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_COMPLETED = "response.completed";
    private static final String EVENT_ERROR = "response.error";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AiTextConfigProperties properties;

    public ResponsesApiTextClient(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        AiTextConfigProperties properties
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        validateProperties(properties);
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return streamText(systemPrompt, userPrompt)
            .reduce(new StringBuilder(), StringBuilder::append)
            .map(StringBuilder::toString)
            .blockOptional()
            .orElse("");
    }

    @Override
    public Flux<String> streamText(String systemPrompt, String userPrompt) {
        return Flux.defer(() -> decodeTextStream(fetchEventStream(systemPrompt, userPrompt)));
    }

    Flux<String> decodeTextStream(Flux<ServerSentEvent<String>> eventFlux) {
        return eventFlux.handle((event, sink) -> {
            String rawData = event.data();
            if (rawData == null || rawData.isBlank()) {
                return;
            }
            if ("[DONE]".equals(rawData.trim())) {
                sink.complete();
                return;
            }

            JsonNode payload;
            try {
                payload = objectMapper.readTree(rawData);
            } catch (JacksonException exception) {
                sink.error(new IllegalStateException("解析 Responses 流式事件失败", exception));
                return;
            }

            String eventType = resolveEventType(event.event(), payload);
            switch (eventType) {
                case EVENT_OUTPUT_TEXT_DELTA -> emitDelta(payload, sink);
                case EVENT_COMPLETED -> sink.complete();
                case EVENT_ERROR -> sink.error(new IllegalStateException(extractErrorMessage(payload)));
                default -> {
                    // 忽略与文本内容无关的其他事件
                }
            }
        });
    }

    private Flux<ServerSentEvent<String>> fetchEventStream(String systemPrompt, String userPrompt) {
        return webClient.post()
            .uri(buildRequestUri())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getResponsesApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(buildRequestBody(systemPrompt, userPrompt))
            .retrieve()
            .bodyToFlux(SSE_TYPE);
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getResponsesModel());
        request.put("stream", true);
        request.put("store", false);
        request.put("input", userPrompt != null ? userPrompt : "");
        request.put("temperature", properties.getResponsesTemperature());

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request.put("instructions", systemPrompt);
        }
        return request;
    }

    private String buildRequestUri() {
        String baseUrl = trimTrailingSlash(properties.getResponsesBaseUrl());
        String path = ensureLeadingSlash(properties.getResponsesPath());
        if (baseUrl.endsWith(path)) {
            return baseUrl;
        }
        return baseUrl + path;
    }

    private String resolveEventType(String eventName, JsonNode payload) {
        if (eventName != null && !eventName.isBlank()) {
            return eventName.trim();
        }
        JsonNode typeNode = payload.get("type");
        return typeNode != null ? typeNode.asText("") : "";
    }

    private void emitDelta(JsonNode payload, reactor.core.publisher.SynchronousSink<String> sink) {
        JsonNode deltaNode = payload.get("delta");
        if (deltaNode == null || deltaNode.isNull()) {
            return;
        }
        String delta = deltaNode.asText("");
        if (!delta.isEmpty()) {
            sink.next(delta);
        }
    }

    private String extractErrorMessage(JsonNode payload) {
        JsonNode errorNode = payload.get("error");
        if (errorNode != null && errorNode.hasNonNull("message")) {
            return errorNode.get("message").asText();
        }
        if (payload.hasNonNull("message")) {
            return payload.get("message").asText();
        }
        return "Responses API 调用失败";
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private void validateProperties(AiTextConfigProperties properties) {
        requireNonBlank(properties.getResponsesBaseUrl(), "缺少配置: app.ai.responses.base-url");
        requireNonBlank(properties.getResponsesPath(), "缺少配置: app.ai.responses.path");
        requireNonBlank(properties.getResponsesApiKey(), "缺少配置: app.ai.responses.api-key");
        requireNonBlank(properties.getResponsesModel(), "缺少配置: app.ai.responses.model");
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
