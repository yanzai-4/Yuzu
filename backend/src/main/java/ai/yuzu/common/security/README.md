# ai.yuzu.common.security

> v0.0.16 🍊 Code-level security helpers shared by every package.

- `SecretScanner` — detects and redacts API keys, bearer tokens, AWS/GitHub tokens, private keys, JWTs
  and password pairs. The LLM gateway redacts EVERY outgoing message with it, so no credential ever
  reaches a model provider or a payload file; the safety gate uses it to block credentials without a
  model call.
