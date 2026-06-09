package interview.guide.common.ai;

public enum ProviderApiType {
  OPENAI_CHAT_COMPLETIONS,
  OPENAI_RESPONSES,
  ANTHROPIC_MESSAGES;

  public static ProviderApiType defaultValue() {
    return OPENAI_CHAT_COMPLETIONS;
  }

  public static ProviderApiType resolve(ProviderApiType apiType) {
    return apiType != null ? apiType : defaultValue();
  }
}
