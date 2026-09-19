package ai.yuzu.llm.structured;

/**
 * v0.0.8 🍊 How a model is forced to answer in JSON, from strongest to weakest.
 *
 * <p>The chosen strategy is remembered per (base URL, model) so prompts stay byte-stable after a downgrade.</p>
 */
public enum OutputStrategy {
    /** v0.0.8 🍊 {@code response_format: json_schema, strict: true} (OpenAI structured outputs). */
    JSON_SCHEMA_STRICT,
    /** v0.0.8 🍊 {@code response_format: json_object} with the schema described in the prompt. */
    JSON_OBJECT,
    /** v0.0.8 🍊 No response_format; schema in the prompt and a tolerant JSON extractor. */
    PROMPT_ONLY;

    /** v0.0.8 🍊 The next weaker strategy (PROMPT_ONLY stays PROMPT_ONLY). */
    public OutputStrategy weaker() {
        return this == JSON_SCHEMA_STRICT ? JSON_OBJECT : PROMPT_ONLY;
    }
}
