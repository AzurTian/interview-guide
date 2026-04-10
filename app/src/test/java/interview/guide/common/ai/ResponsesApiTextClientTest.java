package interview.guide.common.ai;

import interview.guide.common.config.AiTextConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponsesApiTextClientTest {

    @Test
    void decodeTextStreamReturnsOutputTextDeltas() {
        ResponsesApiTextClient client = createClient(WebClient.builder());

        Flux<ServerSentEvent<String>> events = Flux.just(
            ServerSentEvent.<String>builder("{\"type\":\"response.output_text.delta\",\"delta\":\"你\"}")
                .event("response.output_text.delta")
                .build(),
            ServerSentEvent.<String>builder("{\"type\":\"response.output_text.delta\",\"delta\":\"好\"}")
                .event("response.output_text.delta")
                .build(),
            ServerSentEvent.<String>builder("{\"type\":\"response.completed\"}")
                .event("response.completed")
                .build()
        );

        List<String> chunks = client.decodeTextStream(events).collectList().block();

        assertEquals(List.of("你", "好"), chunks);
    }

    @Test
    void generateTextAggregatesStreamingResponse() {
        String sseBody = """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"你"}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"好"}

            event: response.completed
            data: {"type":"response.completed"}

            """;
        WebClient.Builder webClientBuilder = WebClient.builder()
            .exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .body(sseBody)
                    .build()
            ));
        ResponsesApiTextClient client = createClient(webClientBuilder);

        String text = client.generateText("system", "user");

        assertEquals("你好", text);
    }

    @Test
    void decodeTextStreamThrowsWhenErrorEventArrives() {
        ResponsesApiTextClient client = createClient(WebClient.builder());

        Flux<ServerSentEvent<String>> events = Flux.just(
            ServerSentEvent.<String>builder("{\"type\":\"response.error\",\"error\":{\"message\":\"provider failed\"}}")
                .event("response.error")
                .build()
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.decodeTextStream(events).collectList().block()
        );

        assertEquals("provider failed", exception.getMessage());
    }

    @Test
    void streamTextThrowsWhenHttpStatusIsNotSuccess() {
        WebClient.Builder webClientBuilder = WebClient.builder()
            .exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.BAD_GATEWAY)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"message\":\"upstream bad gateway\"}")
                    .build()
            ));
        ResponsesApiTextClient client = createClient(webClientBuilder);

        assertThrows(
            WebClientResponseException.class,
            () -> client.streamText("system", "user").collectList().block()
        );
    }

    private ResponsesApiTextClient createClient(WebClient.Builder webClientBuilder) {
        AiTextConfigProperties properties = new AiTextConfigProperties();
        properties.setResponsesBaseUrl("https://icoe.pp.ua");
        properties.setResponsesPath("/v1/responses");
        properties.setResponsesApiKey("test-key");
        properties.setResponsesModel("test-model");
        properties.setResponsesTemperature(0.2);
        return new ResponsesApiTextClient(webClientBuilder, new ObjectMapper(), properties);
    }
}
