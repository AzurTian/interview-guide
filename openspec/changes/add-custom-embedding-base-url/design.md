## Context

Provider configuration currently stores one `baseUrl` that is used for both chat and embedding.
`LlmProviderRegistry` creates chat models according to `apiType`, while embedding model creation
always builds a Spring AI `OpenAiEmbeddingModel` from the same provider `baseUrl`,
`embeddingModel`, and `embeddingDimensions`.

The knowledge-base module depends on Spring AI `VectorStore`; it receives the project-level
`EmbeddingModel` bean from `LlmEmbeddingConfig`, which delegates to the registry's default
embedding provider. That means the smallest useful change is in provider configuration and
registry embedding model construction, not in knowledge-base services.

Spring AI `OpenAiApi` accepts `baseUrl` and `embeddingsPath` separately. The user-facing
configuration should remain simpler: `embeddingBaseUrl` is a complete endpoint URL, and backend
code adapts it internally.

## Goals / Non-Goals

**Goals:**

- Let a provider use a different complete endpoint URL for embedding calls than for chat calls.
- Preserve existing providers by falling back to the current `baseUrl` behavior when
  `embeddingBaseUrl` is blank.
- Keep embedding protocol support limited to OpenAI-compatible embeddings requests.
- Expose the field consistently through YAML, database-backed provider configuration, REST DTOs,
  and Settings UI.
- Validate malformed embedding endpoint URLs before users set a provider as the default embedding
  provider.

**Non-Goals:**

- Add embedding API type selection.
- Add a separate embedding path field.
- Support arbitrary non-OpenAI-compatible embedding request/response formats.
- Change knowledge-base vectorization, pgvector dimensions, or vector table schema.
- Add a separate embedding API key in this change.

## Decisions

### Interpret `embeddingBaseUrl` as a complete endpoint URL

`embeddingBaseUrl` means the full URL to post embedding requests to, for example
`https://provider.example.com/v1/embeddings`. It is optional. When blank, registry behavior remains
unchanged and uses the provider `baseUrl` with current `ApiPathResolver` path rules.

Alternatives considered:

- Add `embeddingPath`. Rejected because the user explicitly wants direct full URL entry and does
  not want additional path configuration.
- Add `embeddingApiType`. Rejected because this change only targets OpenAI-compatible embedding
  endpoints exposed at custom URLs.

### Split full endpoint URL internally for Spring AI

The registry should adapt a full `embeddingBaseUrl` into the `OpenAiApi` builder's required
`baseUrl` and `embeddingsPath` values. For example:

```text
embeddingBaseUrl = https://embed.example.com/api/v1/embeddings
OpenAiApi.baseUrl = https://embed.example.com
OpenAiApi.embeddingsPath = /api/v1/embeddings
```

This keeps the public configuration simple while avoiding a custom embedding HTTP client.

Alternatives considered:

- Pass the full URL as `baseUrl` and rely on default `/v1/embeddings`. This would duplicate path
  segments and fail for endpoint-specific URLs.
- Implement a custom embedding client. This is unnecessary while Spring AI can be configured with
  a custom path and the protocol remains OpenAI-compatible.

### Keep chat and embedding configuration independent

Chat model creation continues to use `baseUrl` and `apiType`. Embedding model creation uses
`embeddingBaseUrl` only when `supportsEmbedding` is true and an `embeddingModel` is configured.
Provider card display and default embedding confirmation should make it clear which embedding URL
will be used.

Alternatives considered:

- Derive embedding support from `embeddingBaseUrl` alone. Rejected because existing behavior uses
  explicit `supportsEmbedding` plus `embeddingModel`, and endpoint presence does not prove model or
  dimension compatibility.

### Validate as an absolute HTTP(S) URL

When `embeddingBaseUrl` is provided, create/update/default-embedding-provider operations should
reject blank, relative, malformed, or non-HTTP(S) values with `BusinessException(ErrorCode.BAD_REQUEST, ...)`.

Alternatives considered:

- Validate only during actual vectorization. Rejected because it delays configuration errors until
  knowledge-base uploads or re-vectorization jobs fail asynchronously.

## Risks / Trade-offs

- OpenAI-compatible only → Document and validate the field as a URL override, not a protocol
  override. Providers with custom request/response shapes remain unsupported.
- Full endpoint splitting edge cases → Use `URI` parsing and require a non-empty path; add focused
  tests for versioned, nested, trailing-slash, and invalid URLs.
- Existing providers might omit `embeddingBaseUrl` → Keep field nullable and fallback to current
  behavior.
- pgvector dimension mismatch remains possible → Continue validating positive dimensions and keep
  current UI warning that dimensions must match the vector store.
- Cached embedding model may keep old endpoint after update → Continue invoking `registry.reload()`
  after provider configuration changes.

## Migration Plan

1. Add nullable `embeddingBaseUrl` fields to configuration properties, entity, DTOs, and frontend
   types.
2. Let bootstrap and YAML write/read preserve the new `embedding-base-url` value when present.
3. Update provider create/update/default embedding validation.
4. Update `LlmProviderRegistry.createEmbeddingModel()` to use `embeddingBaseUrl` when configured,
   otherwise use the current `baseUrl` path resolution.
5. Update the Settings page form and provider cards.
6. Add unit tests for config service mapping/validation and registry endpoint resolution.

Rollback is safe because existing providers do not require the new field. Removing a configured
`embeddingBaseUrl` returns the provider to the current `baseUrl`-based embedding behavior.

## Open Questions

None.
