## Why

The platform currently treats every chat provider as an OpenAI-compatible
Chat Completions endpoint, which prevents using providers and models exposed
through OpenAI Responses or Anthropic Messages APIs. Adding explicit chat API
type support lets newer models such as OpenAI Responses-based GPT models and
Anthropic Claude models work with the existing interview, RAG, and voice flows,
including streaming output.

## What Changes

- Add a provider chat API type so each provider can be configured as:
  `OPENAI_CHAT_COMPLETIONS`, `OPENAI_RESPONSES`, or `ANTHROPIC_MESSAGES`.
- Preserve existing provider behavior by defaulting legacy providers to
  `OPENAI_CHAT_COMPLETIONS`.
- Support synchronous text generation and streaming text generation for OpenAI
  Responses providers.
- Support synchronous text generation and streaming text generation for
  Anthropic Messages providers.
- Update provider connection testing to use the configured chat API type instead
  of always probing `/chat/completions`.
- Expose the chat API type in backend DTOs and the settings UI so users can add,
  edit, view, and test providers with the correct protocol.
- Keep embedding support on the existing OpenAI-compatible embedding path unless
  a provider explicitly supports embedding configuration.

## Capabilities

### New Capabilities

- `multi-chat-api-provider`: Configure and use chat providers backed by OpenAI
  Chat Completions, OpenAI Responses, or Anthropic Messages APIs with sync and
  streaming text output.

### Modified Capabilities

None.

## Impact

- Backend provider configuration, persistence, bootstrap, YAML editing, DTOs,
  and connection testing.
- `LlmProviderRegistry` chat model creation and cache typing.
- New chat model adapter code for non-Chat-Completions APIs unless an existing
  Spring AI provider implementation can be used cleanly.
- Existing business services that consume `ChatClient` should remain mostly
  unchanged.
- Frontend provider settings types, forms, cards, and presets.
- Tests for provider defaults, persistence compatibility, connection testing,
  sync calls, and streaming calls.
