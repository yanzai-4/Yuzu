package ai.yuzu.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.5 🍊 JSON helpers for database JSON columns and canonical (byte-stable) prompt rendering.
 *
 * <p>{@link #canonical(Object)} sorts map keys so the same data always renders to the same bytes, which
 * keeps LLM prompt prefixes cache-friendly.</p>
 */
@Component
public class Jsons {

    private final ObjectMapper mapper;
    private final ObjectMapper canonicalMapper;

    /** v0.0.5 🍊 Wraps the application ObjectMapper and derives a canonical copy. */
    public Jsons(ObjectMapper mapper) {
        this.mapper = mapper;
        this.canonicalMapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** v0.0.5 🍊 Serializes a value (null-safe: null becomes "null"). */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /** v0.0.5 🍊 Serializes with sorted map keys for byte-stable output. */
    public String canonical(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    /** v0.0.5 🍊 Reads JSON into a type reference (for example a list of strings). */
    public <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON for " + type.getType(), e);
        }
    }

    /** v0.0.5 🍊 Reads JSON into a class. */
    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON for " + type.getSimpleName(), e);
        }
    }

    /** v0.0.5 🍊 Parses JSON into a tree. */
    public JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON", e);
        }
    }

    /** v0.0.5 🍊 Reads a JSON array of strings; null or blank becomes an empty list. */
    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return read(json, new TypeReference<List<String>>() {
        });
    }

    /** v0.0.5 🍊 The application ObjectMapper. */
    public ObjectMapper mapper() {
        return mapper;
    }
}
