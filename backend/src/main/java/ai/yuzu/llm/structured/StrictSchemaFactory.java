package ai.yuzu.llm.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.0.10 🍊 Generates OpenAI-strict JSON Schemas from Java records (the single definition of every LLM output).
 *
 * <p>Rules: every object lists ALL properties in {@code required}, forbids additional properties and keeps
 * properties in record-component order (the order the model generates them, so "reasoning" comes first);
 * optional values use {@link Nullable} → {@code ["type","null"]}; enums become string enums; lists become
 * arrays. The generated schema is cached and byte-stable, because schema bytes are part of the prompt prefix.</p>
 */
@Component
public class StrictSchemaFactory {

    private final ObjectMapper mapper;
    private final Map<Class<?>, ObjectNode> cache = new ConcurrentHashMap<>();

    /** v0.0.10 🍊 Injects the object mapper. */
    public StrictSchemaFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** v0.0.10 🍊 Schema of a record type (cached; do not mutate the returned node). */
    public ObjectNode schemaFor(Class<?> recordType) {
        return cache.computeIfAbsent(recordType, this::objectSchema);
    }

    /** v0.0.10 🍊 Canonical JSON text of the schema (used in prompts for non-strict strategies). */
    public String schemaText(Class<?> recordType) {
        return schemaFor(recordType).toString();
    }

    /** v0.0.10 🍊 Object schema for a record: ordered properties, all required, no extras. */
    private ObjectNode objectSchema(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " must be a record to be used as LLM output");
        }
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = mapper.createArrayNode();
        for (RecordComponent component : type.getRecordComponents()) {
            ObjectNode property = typeSchema(component.getGenericType());
            Desc desc = component.getAnnotation(Desc.class);
            if (desc != null) {
                property = withDescription(property, desc.value());
            }
            if (component.isAnnotationPresent(Nullable.class)) {
                property = nullable(property);
            }
            properties.set(component.getName(), property);
            required.add(component.getName());
        }
        schema.set("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** v0.0.10 🍊 Schema of any supported Java type. */
    private ObjectNode typeSchema(Type type) {
        if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
            ObjectNode array = mapper.createObjectNode();
            array.put("type", "array");
            array.set("items", typeSchema(p.getActualTypeArguments()[0]));
            return array;
        }
        if (!(type instanceof Class<?> c)) {
            throw new IllegalArgumentException("Unsupported LLM output type: " + type);
        }
        ObjectNode node = mapper.createObjectNode();
        if (c == String.class) {
            node.put("type", "string");
        } else if (c == int.class || c == Integer.class || c == long.class || c == Long.class) {
            node.put("type", "integer");
        } else if (c == double.class || c == Double.class || c == float.class || c == Float.class) {
            node.put("type", "number");
        } else if (c == boolean.class || c == Boolean.class) {
            node.put("type", "boolean");
        } else if (c.isEnum()) {
            node.put("type", "string");
            ArrayNode values = node.putArray("enum");
            for (Object constant : c.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
        } else if (c.isRecord()) {
            return objectSchema(c).deepCopy();
        } else {
            throw new IllegalArgumentException("Unsupported LLM output type: " + c.getName());
        }
        return node;
    }

    /** v0.0.10 🍊 Adds a description as the first key (keeps rendering stable). */
    private ObjectNode withDescription(ObjectNode property, String description) {
        ObjectNode described = mapper.createObjectNode();
        described.put("description", description);
        described.setAll(property);
        return described;
    }

    /** v0.0.10 🍊 Makes a property schema accept null (strict-mode optionality). */
    private ObjectNode nullable(ObjectNode property) {
        JsonNode type = property.get("type");
        if ("object".equals(type == null ? null : type.asText())) {
            ObjectNode wrapper = mapper.createObjectNode();
            if (property.has("description")) {
                wrapper.put("description", property.get("description").asText());
                property.remove("description");
            }
            ArrayNode anyOf = wrapper.putArray("anyOf");
            anyOf.add(property);
            anyOf.addObject().put("type", "null");
            return wrapper;
        }
        if (type != null && type.isTextual()) {
            ArrayNode union = mapper.createArrayNode().add(type.asText()).add("null");
            property.set("type", union);
            if (property.has("enum")) {
                ((ArrayNode) property.get("enum")).addNull();
            }
        }
        return property;
    }
}
