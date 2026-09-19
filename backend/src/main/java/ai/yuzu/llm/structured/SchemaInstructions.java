package ai.yuzu.llm.structured;

/**
 * v0.0.10 🍊 Text added to the module instructions (segment S1) when the provider cannot enforce the schema.
 *
 * <p>Strict mode sends the schema through {@code response_format}, so nothing is added (shorter prompt);
 * the other strategies describe the schema in the prompt and mention "JSON" (required by JSON modes).</p>
 */
public final class SchemaInstructions {

    private SchemaInstructions() {
    }

    /** v0.0.10 🍊 Schema instructions for a strategy ("" for strict). */
    public static String forStrategy(OutputStrategy strategy, String schemaText) {
        return switch (strategy) {
            case JSON_SCHEMA_STRICT -> "";
            case JSON_OBJECT, PROMPT_ONLY -> "Reply with exactly one JSON object that matches this JSON Schema "
                    + "(every property is required; use null for optional values; no prose, no code fences):\n"
                    + schemaText;
        };
    }
}
