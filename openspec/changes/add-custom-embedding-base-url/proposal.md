## Why

Some OpenAI-compatible providers expose chat and embedding APIs at different endpoint URLs.
The current provider configuration only has one `baseUrl`, so these providers cannot be used
for knowledge-base vectorization without forcing chat and embedding traffic through the same URL.

## What Changes

- Add an optional `embeddingBaseUrl` provider setting that represents the complete Embedding
  endpoint URL.
- Keep existing behavior when `embeddingBaseUrl` is blank: embedding calls continue to use the
  provider `baseUrl` with the current OpenAI-compatible embeddings path resolution.
- Use `embeddingBaseUrl` only for EmbeddingModel creation; chat model creation remains driven by
  `baseUrl` and `apiType`.
- Expose `embeddingBaseUrl` through provider configuration APIs, persistence, YAML config, and the
  Settings UI.
- Validate `embeddingBaseUrl` as an absolute HTTP(S) URL when provided.
- No breaking changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `multi-chat-api-provider`: Provider configuration can declare a separate full embedding endpoint
  URL while preserving the existing chat provider behavior.

## Impact

- Backend provider configuration: properties, entity, DTOs, bootstrap, YAML read/write, and
  provider validation.
- `LlmProviderRegistry` embedding model construction and cache reload behavior.
- Settings API and Settings page form/card rendering.
- Tests for provider configuration, registry embedding endpoint resolution, and UI request payloads.
