package interview.guide.modules.llmprovider.dto;

import interview.guide.common.ai.ProviderApiType;
import lombok.Builder;

@Builder
public record ProviderDTO(
    String id,
    String baseUrl,
    String maskedApiKey,
    String model,
    ProviderApiType apiType,
    String embeddingModel,
    Integer embeddingDimensions,
    boolean supportsEmbedding,
    Double temperature,
    boolean defaultChatProvider,
    boolean defaultEmbeddingProvider
) {}
