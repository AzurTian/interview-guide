package interview.guide.modules.llmprovider.dto;

import interview.guide.common.ai.ProviderApiType;
import lombok.Builder;

@Builder
public record ProviderDTO(
    String id,
    String baseUrl,
    String maskedApiKey,
    String maskedEmbeddingApiKey,
    String model,
    ProviderApiType apiType,
    String embeddingModel,
    Integer embeddingDimensions,
    String embeddingBaseUrl,
    boolean supportsEmbedding,
    Double temperature,
    boolean defaultChatProvider,
    boolean defaultEmbeddingProvider
) {}
