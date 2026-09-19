# ai.yuzu.llm.structured

> v0.0.10 🍊 Guaranteed JSON output: dedicated method, format checks, 3 retries.

- `StructuredCaller` — 1 call + up to 3 format retries. Each attempt: extract JSON → parse → validate
  against the exact schema sent to the provider → map to the record → module `SemanticCheck`. Feedback
  (invalid answer + error list) is appended at the END of the prompt so the cached prefix is untouched.
  `finish_reason=length` doubles the output budget; refusals / content-filter stops are not retried.
  A provider that rejects `response_format` downgrades the strategy and the prompt is rebuilt; downgrades
  do not consume retries.
- `OutputStrategy` — JSON_SCHEMA_STRICT → JSON_OBJECT → PROMPT_ONLY, remembered per (base URL, model).
- `StrictSchemaFactory` — our own tiny record → OpenAI-strict schema generator: properties in record
  component order (reasoning first), all required, `additionalProperties:false`, `@Nullable` →
  `["type","null"]`, `@Desc` descriptions, enums, lists, nested records. Cached and byte-stable.
  (Chosen over a generic library: the strict subset is small and property order must be exact.)
- `SchemaValidator` — networknt JSON Schema 2020-12 validation with compiled schemas cached per type.
- `JsonExtractor` — first balanced JSON object from text (fences and prose tolerated).
- `SchemaInstructions` — schema text for S1 when the provider cannot enforce the schema.
- `SemanticCheck`, `StructuredResult`, `Nullable`, `Desc` — supporting types.
