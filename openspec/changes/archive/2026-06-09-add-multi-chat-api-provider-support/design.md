## Context

The backend currently stores provider configuration as `baseUrl`, `apiKey`,
`model`, optional embedding settings, and `temperature`. `LlmProviderRegistry`
uses those fields to create `OpenAiChatModel` instances through Spring AI, so
all chat traffic is routed as OpenAI-compatible Chat Completions. Provider
connection testing also probes `/chat/completions`.

The business modules already depend on Spring AI `ChatClient` for synchronous
and streaming text generation. RAG and voice interview streaming are implemented
through `ChatClient.prompt().stream().content()`, so the lowest-impact path is
to keep that contract stable and vary the underlying `ChatModel` by provider
type.

## Goals / Non-Goals

**Goals:**

- Allow each provider to declare a chat API type:
  `OPENAI_CHAT_COMPLETIONS`, `OPENAI_RESPONSES`, or `ANTHROPIC_MESSAGES`.
- Preserve all existing provider configurations by treating missing API type as
  `OPENAI_CHAT_COMPLETIONS`.
- Support synchronous text generation and streaming text generation for all
  three API types.
- Keep existing business service usage of `ChatClient` intact where possible.
- Make provider testing, settings APIs, and settings UI reflect the configured
  API type.

**Non-Goals:**

- Add tool calling support for OpenAI Responses or Anthropic Messages providers
  in the first version.
- Add Anthropic or Responses embedding support.
- Add multimodal input/output support.
- Change the public RAG, interview, schedule, resume, or voice interview API
  contracts beyond selecting and using provider configuration.

## Decisions

### Use an explicit provider API type

Add a `ProviderApiType` enum and persist it on provider configuration. Existing
rows and legacy YAML properties default to `OPENAI_CHAT_COMPLETIONS`.

Alternatives considered:

- Infer the type from base URL or model name. This is brittle because gateways
  can expose non-standard URLs and model names do not reliably identify API
  protocols.
- Split providers into separate tables. This adds migration complexity without
  improving the user-facing configuration model.

### Keep `ChatClient` as the business-facing abstraction

Change `LlmProviderRegistry` to cache `ChatModel` rather than
`OpenAiChatModel`, then create `ChatClient` from the selected model. The
existing OpenAI-compatible path remains unchanged internally.

Alternatives considered:

- Replace business services with a custom project-level LLM client. This would
  touch many modules and duplicate Spring AI prompt/advisor integration.
- Call provider REST APIs directly inside each module. This would scatter API
  protocol logic across business services and make streaming harder to maintain.

### Implement non-compatible APIs as text-only `ChatModel` adapters

Create adapter implementations that map Spring AI `Prompt` messages to provider
requests and map provider responses back to `ChatResponse` / `Flux<ChatResponse>`.
The first version supports system, user, and assistant text messages.

OpenAI Responses:

- Sync request: `POST {baseUrl}/responses` with `model`, text input, optional
  temperature, and no streaming flag.
- Streaming request: same endpoint with streaming enabled.
- Streaming parser extracts text deltas from Responses SSE events such as
  `response.output_text.delta`.

Anthropic Messages:

- Sync request: `POST {baseUrl}/v1/messages` or `{baseUrl}` when the configured
  base URL already ends with `/messages`, with Anthropic authorization headers.
- Streaming request: same endpoint with `stream: true`.
- Streaming parser extracts text deltas from `content_block_delta` events with
  `text_delta` payloads.

Alternatives considered:

- Upgrade Spring AI and rely entirely on built-in provider implementations. This
  may be viable, but current dependencies only include the OpenAI starter and
  the implementation must support dynamic per-provider base URL/API key/model
  configuration. If a built-in implementation satisfies that cleanly, use it;
  otherwise keep the custom adapters scoped and text-only.

### Keep embedding on the OpenAI-compatible path

Embedding creation continues to use the existing OpenAI-compatible embedding
model path. Providers whose chat API type is `OPENAI_RESPONSES` or
`ANTHROPIC_MESSAGES` can still disable embedding, or use embedding settings only
when their base URL also supports the existing OpenAI-compatible embeddings API.

Alternatives considered:

- Add Anthropic/Responses embedding dialects in this change. This expands scope
  beyond the requested chat provider and streaming support.

### Make connection tests protocol-aware

Provider tests build the target URL, headers, and minimal request body from the
configured API type:

- Chat Completions: existing `/chat/completions` probe.
- Responses: `/responses` probe requesting a one-token or low-token text output.
- Anthropic Messages: `/v1/messages` probe with a minimal user message and
  provider-specific headers.

## Risks / Trade-offs

- Non-compatible adapters may not support tool calls → Gate tool advisors for
  unsupported API types and fail with a clear configuration message if a flow
  requires tools.
- Provider APIs have different SSE event formats → Add focused parser tests for
  OpenAI Responses and Anthropic Messages stream fixtures.
- Existing database rows lack API type → Use a non-null default in entity/model
  loading and a compatible schema default where possible.
- `temperature` support varies by model/provider → Include it only when
  configured and avoid requiring it for connectivity.
- Base URL conventions vary → Normalize URL construction and accept both service
  roots and endpoint-specific URLs where practical.
- Custom adapters bypass some Spring AI provider metadata → Preserve text
  behavior first, then include basic model/id/usage metadata when available.

## Migration Plan

1. Add the API type field to configuration objects, DTOs, entity, bootstrap, and
   YAML read/write paths.
2. Default missing API type values to `OPENAI_CHAT_COMPLETIONS` so existing
   providers keep working.
3. Update `LlmProviderRegistry` to construct chat models by API type.
4. Add protocol-aware connection tests.
5. Update the settings UI to show and edit API type.
6. Verify existing OpenAI-compatible providers still pass tests before enabling
   Responses or Anthropic providers.

Rollback is to set all providers back to `OPENAI_CHAT_COMPLETIONS` and use the
existing compatible endpoints. Since the new field defaults to the old behavior,
old providers remain usable during rollback.

## Open Questions

- Should Responses/Anthropic providers be allowed for voice interview flows when
  tool advisors are enabled globally, or should voice clients automatically use a
  text-only advisor set for unsupported tool APIs?
- Should the UI offer provider presets for OpenAI Responses and Anthropic, or
  only expose a generic API type selector in the first version?
