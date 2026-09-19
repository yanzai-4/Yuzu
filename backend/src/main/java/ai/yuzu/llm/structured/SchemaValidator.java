package ai.yuzu.llm.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.0.10 🍊 Validates model output against the SAME schema that was sent to the provider (networknt, 2020-12).
 *
 * <p>Compiled validators are cached per output type. Even in strict mode every answer is validated,
 * because some gateways accept {@code json_schema} but ignore it silently.</p>
 */
@Component
public class SchemaValidator {

    private final StrictSchemaFactory schemas;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<Class<?>, JsonSchema> compiled = new ConcurrentHashMap<>();

    /** v0.0.10 🍊 Injects the schema factory. */
    public SchemaValidator(StrictSchemaFactory schemas) {
        this.schemas = schemas;
    }

    /** v0.0.10 🍊 Human-readable validation errors ("$.mode: does not have a value in the enumeration ..."). */
    public List<String> validate(Class<?> type, JsonNode node) {
        JsonSchema schema = compiled.computeIfAbsent(type, t -> factory.getSchema(schemas.schemaFor(t)));
        Set<ValidationMessage> messages = schema.validate(node);
        return messages.stream().map(ValidationMessage::getMessage).sorted().toList();
    }
}
