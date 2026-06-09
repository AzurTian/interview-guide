package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

import java.util.regex.Pattern;

public final class ApiKeySanitizer {

  private static final Pattern BEARER_PREFIX =
      Pattern.compile("^Bearer\\s+", Pattern.CASE_INSENSITIVE);

  private ApiKeySanitizer() {
  }

  public static String normalize(String apiKey) {
    if (apiKey == null) {
      return "";
    }
    return BEARER_PREFIX.matcher(apiKey.trim()).replaceFirst("").trim();
  }

  public static String requirePresent(String apiKey, String providerName) {
    String normalized = normalize(apiKey);
    if (normalized.isBlank()) {
      throw new BusinessException(ErrorCode.AI_API_KEY_INVALID,
          providerName + " API Key 为空，请检查 Provider 配置或环境变量是否已加载");
    }
    return normalized;
  }
}
