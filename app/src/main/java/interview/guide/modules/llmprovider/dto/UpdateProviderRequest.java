package interview.guide.modules.llmprovider.dto;

import interview.guide.common.ai.ProviderApiType;

public record UpdateProviderRequest(
    String baseUrl,
    String apiKey,
    String model,
    ProviderApiType apiType,
    String embeddingModel,
    Integer embeddingDimensions,
    Boolean supportsEmbedding,
    Double temperature
) {
    public UpdateProviderRequest(
        String baseUrl,
        String apiKey,
        String model,
        String embeddingModel,
        Double temperature
    ) {
        this(baseUrl, apiKey, model, null, embeddingModel, null, null, temperature);
    }
}
