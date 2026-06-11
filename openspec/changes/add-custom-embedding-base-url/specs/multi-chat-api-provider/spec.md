## ADDED Requirements

### Requirement: Provider embedding endpoint URL
The system SHALL allow every provider to declare an optional `embeddingBaseUrl` that represents the
complete HTTP(S) endpoint URL used for OpenAI-compatible embedding requests.

#### Scenario: Provider stores embedding endpoint URL
- **WHEN** a user creates or updates a provider with a non-empty `embeddingBaseUrl`
- **THEN** the system persists the value and returns it from provider read APIs

#### Scenario: Provider omits embedding endpoint URL
- **WHEN** a provider does not configure `embeddingBaseUrl`
- **THEN** the provider remains valid and retains the existing base URL based embedding behavior

### Requirement: Embedding model uses configured endpoint URL
The system SHALL use `embeddingBaseUrl` for embedding model creation when it is configured, while
chat model creation SHALL continue to use the provider `baseUrl` and chat API type.

#### Scenario: Embedding endpoint overrides provider base URL
- **WHEN** a provider has `supportsEmbedding=true`, an `embeddingModel`, and an `embeddingBaseUrl`
- **THEN** embedding requests are sent to the configured `embeddingBaseUrl`

#### Scenario: Chat endpoint is unaffected
- **WHEN** a provider has both `baseUrl` and `embeddingBaseUrl`
- **THEN** chat requests continue to use `baseUrl` according to the provider chat API type

#### Scenario: Embedding endpoint fallback
- **WHEN** a provider supports embedding but does not configure `embeddingBaseUrl`
- **THEN** embedding requests use the existing provider `baseUrl` based OpenAI-compatible embedding path resolution

### Requirement: Embedding endpoint URL validation
The system SHALL validate configured `embeddingBaseUrl` values before saving provider configuration
or setting a provider as the default embedding provider.

#### Scenario: Valid embedding endpoint URL
- **WHEN** a user configures `embeddingBaseUrl` as an absolute `http` or `https` URL with a path
- **THEN** the provider configuration is accepted

#### Scenario: Invalid embedding endpoint URL
- **WHEN** a user configures `embeddingBaseUrl` as a blank, relative, malformed, or non-HTTP(S) URL
- **THEN** the system rejects the provider configuration with a business validation error

### Requirement: Settings UI supports embedding endpoint URL
The settings UI SHALL allow users to view and edit a provider's `embeddingBaseUrl` when embedding
support is enabled.

#### Scenario: Provider form submits embedding endpoint URL
- **WHEN** a user creates or updates a provider with embedding support enabled and enters an embedding endpoint URL
- **THEN** the submitted provider request includes `embeddingBaseUrl`

#### Scenario: Provider card shows embedding endpoint URL
- **WHEN** a provider has an `embeddingBaseUrl`
- **THEN** the settings provider card displays the configured embedding endpoint URL as the actual embedding endpoint

#### Scenario: Embedding endpoint cleared
- **WHEN** a user clears `embeddingBaseUrl` and saves the provider
- **THEN** subsequent embedding calls fall back to the provider `baseUrl` based behavior
