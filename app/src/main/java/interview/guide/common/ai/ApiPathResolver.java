package interview.guide.common.ai;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public final class ApiPathResolver {

  private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
  private static final int DEFAULT_READ_TIMEOUT = 300000;

  private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

  private ApiPathResolver() {}

  public static OpenAiApi buildOpenAiApi(String baseUrl, String apiKey) {
    return buildOpenAiApi(baseUrl, apiKey, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  public static OpenAiApi buildOpenAiApi(String baseUrl, String apiKey,
      int connectTimeout, int readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);

    RestClient.Builder restClientBuilder = RestClient.builder()
        .requestFactory(requestFactory);

    OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .restClientBuilder(restClientBuilder);
    if (baseUrlContainsVersion(baseUrl)) {
      apiBuilder.completionsPath("/chat/completions").embeddingsPath("/embeddings");
    }
    return apiBuilder.build();
  }

  public static OpenAiApi buildOpenAiEmbeddingApi(String embeddingBaseUrl, String apiKey) {
    return buildOpenAiEmbeddingApi(embeddingBaseUrl, apiKey, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  public static OpenAiApi buildOpenAiEmbeddingApi(String embeddingBaseUrl, String apiKey,
      int connectTimeout, int readTimeout) {
    EmbeddingEndpoint endpoint = resolveEmbeddingEndpoint(embeddingBaseUrl);
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);

    RestClient.Builder restClientBuilder = RestClient.builder()
        .requestFactory(requestFactory);

    return OpenAiApi.builder()
        .baseUrl(endpoint.baseUrl())
        .apiKey(apiKey)
        .embeddingsPath(endpoint.embeddingsPath())
        .restClientBuilder(restClientBuilder)
        .build();
  }

  public static EmbeddingEndpoint resolveEmbeddingEndpoint(String embeddingBaseUrl) {
    try {
      URI uri = new URI(stripTrailingSlashes(embeddingBaseUrl));
      String scheme = uri.getScheme();
      if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
        throw new IllegalArgumentException("embeddingBaseUrl must use http or https");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("embeddingBaseUrl must include a host");
      }
      if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
        throw new IllegalArgumentException("embeddingBaseUrl must not include query or fragment");
      }
      String path = uri.getRawPath();
      if (path == null || path.isBlank() || "/".equals(path)) {
        throw new IllegalArgumentException("embeddingBaseUrl must include an endpoint path");
      }
      String baseUrl = new URI(
          uri.getScheme(),
          uri.getRawUserInfo(),
          uri.getHost(),
          uri.getPort(),
          null,
          null,
          null
      ).toString();
      return new EmbeddingEndpoint(baseUrl, path);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("embeddingBaseUrl is not a valid URI", e);
    }
  }

  public static boolean baseUrlContainsVersion(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return false;
    }
    String stripped = stripTrailingSlashes(baseUrl.trim());
    return TRAILING_VERSION.matcher(stripped).find();
  }

  public static String stripTrailingSlashes(String value) {
    if (value == null) {
      return "";
    }
    String result = value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  public record EmbeddingEndpoint(String baseUrl, String embeddingsPath) {
  }
}
