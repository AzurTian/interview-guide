## ADDED Requirements

### Requirement: Provider chat API type
The system SHALL allow every chat provider to declare one chat API type from
`OPENAI_CHAT_COMPLETIONS`, `OPENAI_RESPONSES`, and `ANTHROPIC_MESSAGES`.
Providers without a stored or configured API type SHALL be treated as
`OPENAI_CHAT_COMPLETIONS`.

#### Scenario: Existing provider defaults to Chat Completions
- **WHEN** a provider record or YAML provider entry does not include a chat API type
- **THEN** the system uses `OPENAI_CHAT_COMPLETIONS` for chat model creation and connection testing

#### Scenario: New provider stores selected API type
- **WHEN** a user creates or updates a provider with `OPENAI_RESPONSES` or `ANTHROPIC_MESSAGES`
- **THEN** the selected API type is persisted and returned by provider read APIs

### Requirement: Protocol-aware chat model creation
The system SHALL create the provider chat model according to the provider chat
API type while continuing to expose Spring AI `ChatClient` to business services.

#### Scenario: OpenAI-compatible provider uses existing model path
- **WHEN** a provider has API type `OPENAI_CHAT_COMPLETIONS`
- **THEN** the registry creates a chat model using the existing OpenAI-compatible Chat Completions implementation

#### Scenario: OpenAI Responses provider uses Responses path
- **WHEN** a provider has API type `OPENAI_RESPONSES`
- **THEN** the registry creates a chat model that sends synchronous and streaming chat requests to the OpenAI Responses API

#### Scenario: Anthropic provider uses Messages path
- **WHEN** a provider has API type `ANTHROPIC_MESSAGES`
- **THEN** the registry creates a chat model that sends synchronous and streaming chat requests to the Anthropic Messages API

### Requirement: Synchronous text generation
The system SHALL support synchronous text generation for all supported provider
chat API types.

#### Scenario: Responses sync call returns text
- **WHEN** a business service calls `ChatClient.prompt().call().content()` through an `OPENAI_RESPONSES` provider
- **THEN** the system returns the text content from the Responses output

#### Scenario: Anthropic sync call returns text
- **WHEN** a business service calls `ChatClient.prompt().call().content()` through an `ANTHROPIC_MESSAGES` provider
- **THEN** the system returns the text content from the Messages response

### Requirement: Streaming text generation
The system SHALL support streaming text generation for all supported provider
chat API types.

#### Scenario: Responses stream emits text deltas
- **WHEN** a business service calls `ChatClient.prompt().stream().content()` through an `OPENAI_RESPONSES` provider
- **THEN** the system emits text chunks parsed from Responses streaming delta events

#### Scenario: Anthropic stream emits text deltas
- **WHEN** a business service calls `ChatClient.prompt().stream().content()` through an `ANTHROPIC_MESSAGES` provider
- **THEN** the system emits text chunks parsed from Anthropic Messages streaming delta events

#### Scenario: Existing SSE endpoints continue streaming
- **WHEN** the RAG or voice interview streaming endpoints use a non-Chat-Completions provider
- **THEN** the endpoints continue returning streamed text to clients without changing their public API contract

### Requirement: Protocol-aware provider connection testing
The system SHALL test provider connectivity using the configured chat API type
rather than always using the Chat Completions endpoint.

#### Scenario: Chat Completions provider test
- **WHEN** a provider has API type `OPENAI_CHAT_COMPLETIONS`
- **THEN** the connection test sends a minimal request to the Chat Completions endpoint

#### Scenario: Responses provider test
- **WHEN** a provider has API type `OPENAI_RESPONSES`
- **THEN** the connection test sends a minimal request to the Responses endpoint

#### Scenario: Anthropic provider test
- **WHEN** a provider has API type `ANTHROPIC_MESSAGES`
- **THEN** the connection test sends a minimal request to the Anthropic Messages endpoint with Anthropic-compatible headers

### Requirement: Settings UI supports API type
The settings UI SHALL let users view and edit a provider's chat API type.

#### Scenario: Provider card shows API type
- **WHEN** the settings page lists providers
- **THEN** each provider card displays its configured chat API type

#### Scenario: Provider form submits API type
- **WHEN** a user creates or updates a provider from the settings page
- **THEN** the submitted request includes the selected chat API type

### Requirement: Unsupported advanced features are guarded
The system SHALL avoid silently enabling unsupported advanced features for
provider chat API types whose adapters do not implement them.

#### Scenario: Tool calling is unsupported for text-only adapters
- **WHEN** a flow attempts to use tool calling with a provider API type that does not support tool calling
- **THEN** the system disables tool advisors for that provider or returns a clear provider configuration error

#### Scenario: Embedding remains explicitly configured
- **WHEN** a provider uses `OPENAI_RESPONSES` or `ANTHROPIC_MESSAGES`
- **THEN** the system does not assume embedding support unless the provider has explicit embedding configuration
