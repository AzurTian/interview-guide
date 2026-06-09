## 1. Provider Configuration Model

- [x] 1.1 Add a `ProviderApiType` enum with `OPENAI_CHAT_COMPLETIONS`, `OPENAI_RESPONSES`, and `ANTHROPIC_MESSAGES`.
- [x] 1.2 Add chat API type fields to provider properties, entity, DTOs, create/update requests, runtime snapshots, and runtime config records.
- [x] 1.3 Default missing API type values to `OPENAI_CHAT_COMPLETIONS` for existing database rows and legacy YAML providers.
- [x] 1.4 Update provider bootstrap and YAML write/edit paths to preserve and persist API type.

## 2. Chat Model Adapters

- [x] 2.1 Implement or wire an OpenAI Responses text-only `ChatModel` supporting `call(Prompt)`.
- [x] 2.2 Implement OpenAI Responses streaming support that parses Responses SSE text delta events into `Flux<ChatResponse>`.
- [x] 2.3 Implement or wire an Anthropic Messages text-only `ChatModel` supporting `call(Prompt)`.
- [x] 2.4 Implement Anthropic Messages streaming support that parses Messages SSE text delta events into `Flux<ChatResponse>`.
- [x] 2.5 Add focused unit tests for prompt-to-request mapping and stream delta parsing for both new API types.

## 3. Registry Integration

- [x] 3.1 Change `LlmProviderRegistry` chat model cache from `OpenAiChatModel` to Spring AI `ChatModel`.
- [x] 3.2 Select the chat model implementation by provider API type while preserving the existing OpenAI-compatible path.
- [x] 3.3 Gate or disable tool advisors for provider API types whose adapters do not support tool calling, with clear logging or configuration errors.
- [x] 3.4 Verify existing sync and streaming business flows still consume `ChatClient` without public API changes.

## 4. Provider Connectivity Testing

- [x] 4.1 Replace fixed `/chat/completions` probe construction with protocol-aware endpoint, header, and request body builders.
- [x] 4.2 Add OpenAI Responses connection test support.
- [x] 4.3 Add Anthropic Messages connection test support with Anthropic-compatible headers.
- [x] 4.4 Add tests covering success and failure messaging for each provider API type.

## 5. Settings UI

- [x] 5.1 Add API type fields to frontend provider types and API request payloads.
- [x] 5.2 Add an API type selector to provider create/edit forms.
- [x] 5.3 Display API type on provider cards and preserve existing embedding controls.
- [x] 5.4 Add presets or defaults for OpenAI Responses and Anthropic Messages providers.

## 6. Verification

- [x] 6.1 Add or update backend tests for legacy provider defaulting and registry model selection.
- [x] 6.2 Add or update frontend tests or type checks for provider API type form handling.
- [x] 6.3 Run backend tests relevant to provider registry, provider config service, and streaming adapters.
- [x] 6.4 Run frontend type check/build for settings UI changes.
