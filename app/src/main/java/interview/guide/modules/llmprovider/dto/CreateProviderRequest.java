package interview.guide.modules.llmprovider.dto;

import interview.guide.common.ai.ProviderApiType;
import jakarta.validation.constraints.NotBlank;

public record CreateProviderRequest(
    @NotBlank String id,
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    @NotBlank String model,
    ProviderApiType apiType,
    String embeddingModel,
    Integer embeddingDimensions,
    Boolean supportsEmbedding,
    Double temperature
) {
    public CreateProviderRequest(
        String id,
        String baseUrl,
        String apiKey,
        String model,
        String embeddingModel,
        Double temperature
    ) {
        this(id, baseUrl, apiKey, model, null, embeddingModel, null, null, temperature);
    }
}
