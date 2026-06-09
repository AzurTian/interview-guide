package interview.guide.common.ai;

import interview.guide.common.exception.ErrorCode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StructuredOutputInvoker 测试")
class StructuredOutputInvokerTest {

  @Test
  @DisplayName("默认使用流式响应聚合结构化输出")
  void invokeUsesStreamByDefault() {
    StreamingOnlyChatModel model = new StreamingOnlyChatModel();
    StructuredOutputInvoker invoker = new StructuredOutputInvoker(new StructuredOutputProperties(), null);

    TestOutput output = invoker.invoke(
        ChatClient.create(model),
        "format",
        "user",
        new BeanOutputConverter<>(TestOutput.class),
        ErrorCode.AI_SERVICE_ERROR,
        "失败：",
        "测试",
        org.slf4j.LoggerFactory.getLogger(StructuredOutputInvokerTest.class)
    );

    assertThat(output.message()).isEqualTo("ok");
    assertThat(model.streamCalled).isTrue();
    assertThat(model.callCalled).isFalse();
  }

  @Test
  @DisplayName("关闭流式后使用非流式响应")
  void invokeUsesCallWhenStreamDisabled() {
    StructuredOutputProperties properties = new StructuredOutputProperties();
    properties.setStructuredStreamEnabled(false);
    CallOnlyChatModel model = new CallOnlyChatModel();
    StructuredOutputInvoker invoker = new StructuredOutputInvoker(properties, null);

    TestOutput output = invoker.invoke(
        ChatClient.create(model),
        "format",
        "user",
        new BeanOutputConverter<>(TestOutput.class),
        ErrorCode.AI_SERVICE_ERROR,
        "失败：",
        "测试",
        org.slf4j.LoggerFactory.getLogger(StructuredOutputInvokerTest.class)
    );

    assertThat(output.message()).isEqualTo("ok");
    assertThat(model.callCalled.get()).isTrue();
  }

  private record TestOutput(String message) {
  }

  private static class StreamingOnlyChatModel implements ChatModel {
    private final ChatOptions options = ChatOptions.builder().model("test").build();
    private boolean streamCalled;
    private boolean callCalled;

    @Override
    public ChatResponse call(Prompt prompt) {
      callCalled = true;
      throw new IllegalStateException("call should not be used");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      streamCalled = true;
      return Flux.just(response("{\"message\""), response(":\"ok\"}"));
    }

    @Override
    public ChatOptions getDefaultOptions() {
      return options;
    }
  }

  private static class CallOnlyChatModel implements ChatModel {
    private final ChatOptions options = ChatOptions.builder().model("test").build();
    private final AtomicBoolean callCalled = new AtomicBoolean(false);

    @Override
    public ChatResponse call(Prompt prompt) {
      callCalled.set(true);
      return response("{\"message\":\"ok\"}");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      throw new IllegalStateException("stream should not be used");
    }

    @Override
    public ChatOptions getDefaultOptions() {
      return options;
    }
  }

  private static ChatResponse response(String text) {
    AssistantMessage message = AssistantMessage.builder()
        .content(text)
        .build();
    return ChatResponse.builder()
        .metadata(ChatResponseMetadata.builder().model("test").build())
        .generations(List.of(new Generation(message)))
        .build();
  }
}
