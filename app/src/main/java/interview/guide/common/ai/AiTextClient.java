package interview.guide.common.ai;

import reactor.core.publisher.Flux;

/**
 * 统一抽象文本生成客户端，屏蔽不同上游供应商的协议差异。
 */
public interface AiTextClient {

    /**
     * 生成完整文本响应。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 完整文本
     */
    String generateText(String systemPrompt, String userPrompt);

    /**
     * 流式生成文本响应。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 文本增量流
     */
    Flux<String> streamText(String systemPrompt, String userPrompt);
}
