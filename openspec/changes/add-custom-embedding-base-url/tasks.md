## 1. Backend Provider Configuration

- [x] 1.1 Add nullable `embeddingBaseUrl` to provider properties, entity, DTOs, and provider snapshot mapping.
- [x] 1.2 Update provider bootstrap and YAML read/write paths to preserve `embedding-base-url` when configured.
- [x] 1.3 Update create/update provider flows to trim, persist, return, and clear `embeddingBaseUrl`.
- [x] 1.4 Add validation that non-empty `embeddingBaseUrl` is an absolute HTTP(S) URL with a non-empty path.
- [x] 1.5 Include `embeddingBaseUrl` validation when setting a provider as the default embedding provider.

## 2. Embedding Model Routing

- [x] 2.1 Add a small endpoint resolver that converts a full embedding endpoint URL into Spring AI `baseUrl` and `embeddingsPath` values.
- [x] 2.2 Update `LlmProviderRegistry.createEmbeddingModel()` to use `embeddingBaseUrl` when present and keep the existing `baseUrl` fallback otherwise.
- [x] 2.3 Ensure provider cache reload still occurs after changes that affect embedding endpoint routing.

## 3. Settings UI

- [x] 3.1 Add `embeddingBaseUrl` to frontend provider types and create/update request payloads.
- [x] 3.2 Add a Settings form field for `embeddingBaseUrl` that is shown when embedding support is enabled.
- [x] 3.3 Populate, submit, and clear `embeddingBaseUrl` correctly while editing providers.
- [x] 3.4 Display the configured embedding endpoint URL on provider cards and default embedding confirmation text.

## 4. Tests and Verification

- [x] 4.1 Add backend tests for provider create/update/read behavior with `embeddingBaseUrl`.
- [x] 4.2 Add backend tests for invalid embedding endpoint URL validation.
- [x] 4.3 Add registry tests for endpoint splitting and fallback embedding base URL behavior.
- [x] 4.4 Add or update frontend tests/types checks for Settings provider payload handling.
- [x] 4.5 Run backend and frontend verification commands relevant to the touched code.
