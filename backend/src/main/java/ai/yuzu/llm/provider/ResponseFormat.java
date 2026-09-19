package ai.yuzu.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;

/** v0.0.8 🍊 The {@code response_format} to request (none, JSON object mode or a strict JSON schema). */
public sealed interface ResponseFormat {

    /** v0.0.8 🍊 Plain text answer. */
    record None() implements ResponseFormat {
    }

    /** v0.0.8 🍊 {@code {"type":"json_object"}}. */
    record JsonObject() implements ResponseFormat {
    }

    /** v0.0.8 🍊 {@code {"type":"json_schema","json_schema":{"name":..,"strict":true,"schema":..}}}. */
    record JsonSchema(String name, JsonNode schema) implements ResponseFormat {
    }

    /** v0.0.8 🍊 Shared instance for plain text. */
    ResponseFormat NONE = new None();

    /** v0.0.8 🍊 Shared instance for JSON object mode. */
    ResponseFormat JSON_OBJECT = new JsonObject();
}
