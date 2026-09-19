# ai.yuzu.settings

> v0.0.7 🍊 The console's model settings and encrypted API keys.

- `SettingsService` — provider (`OPENAI`, `EDGEONE`, `CUSTOM`), base URL and one `TierSettings` per
  `ModelTier` (IMPORTANT / DEFAULT / LIGHT: model, reasoning effort, max output tokens). Held in memory
  and refreshed on write; `revision()` lets the LLM layer rebuild clients. Publishes `settings.changed`.
  `listModels()` calls `GET {baseUrl}/models`.
- `SecretVault` — AES-256-GCM with a 32-byte master key from `YUZU_MASTER_KEY` or
  `<secret-dir>/master.key` (auto-generated, 0600, outside the repo). Ciphertexts are bound to their
  provider through AAD. Keys are only ever returned masked (`sk-…abcd`).
- `AppSettingRepository` — the `app_setting` key/value JSON table.
- `LlmSettings`, `TierSettings`, `LlmProvider`, `LlmSettingsView` — settings records; defaults are
  OpenAI `gpt-5.5` / `gpt-5-mini` / `gpt-5-nano` (editable).
- `SettingsController` — `GET/PUT /api/settings/llm`, `PUT /api/settings/llm/key`,
  `GET /api/settings/llm/models` (the per-tier test endpoint arrives with the capability probe).
- `SettingsSnapshotContributor` — masked settings for `/api/bootstrap`.
