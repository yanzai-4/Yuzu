# ai.yuzu.llm.provider

> v0.0.8 🍊 Wire level: one HTTP exchange with an OpenAI-compatible endpoint.

- `OpenAiCompatibleProvider` — `POST {baseUrl}/chat/completions` over HTTP/1.1 on virtual threads;
  interruptible through the `CancelToken`; parses normal and streamed (`data:` / `[DONE]`) answers,
  reads usage from the final stream chunk, aborts streams idle for 45 s. Maps 401/403 to `LlmAuthException`,
  429/5xx/408 to retryable `LlmTransportException` (with Retry-After), other 4xx to
  `ProviderRequestException` (with the offending `param`).
- `LlmRequest` (fully shaped body), `LlmResult` (text, finish reason, refusal, usage, latency, TTFT,
  request/response JSON for tracing), `LlmMessage`, `ResponseFormat` (none / json_object / strict
  json_schema), `StreamSink`, `ProviderEndpoint` (never prints the key), `ChatProvider`.
