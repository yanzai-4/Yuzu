package ai.yuzu.tool.spi;

import ai.yuzu.agent.PermissionScope;
import ai.yuzu.llm.structured.SchemaValidator;
import ai.yuzu.llm.structured.StrictSchemaFactory;
import ai.yuzu.module.ToolCatalogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * v0.0.18 🍊 All tools, looked up by name; renders the tool catalogs shown to the models (sorted, byte-stable).
 */
@Component
public class ToolRegistry implements ToolCatalogProvider {

    private final ObjectProvider<Tool<?>> tools;
    private final StrictSchemaFactory schemas;
    private final SchemaValidator validator;
    private volatile Map<String, Tool<?>> byName;

    /** v0.0.18 🍊 Collects every Tool bean lazily. */
    public ToolRegistry(ObjectProvider<Tool<?>> tools, StrictSchemaFactory schemas, SchemaValidator validator) {
        this.tools = tools;
        this.schemas = schemas;
        this.validator = validator;
    }

    /** v0.0.18 🍊 Tool by name. */
    public Optional<Tool<?>> find(String name) {
        return Optional.ofNullable(index().get(name));
    }

    /** v0.0.18 🍊 Every tool, sorted by name. */
    public List<Tool<?>> all() {
        return List.copyOf(index().values());
    }

    /** v0.0.18 🍊 True when the scope holds every permission the tool needs. */
    public static boolean permitted(Tool<?> tool, PermissionScope scope) {
        return tool.spec().permissions().stream().allMatch(scope::has);
    }

    /** v0.0.18 🍊 Validation errors of arguments against a tool's argument schema. */
    public List<String> validateArgs(Tool<?> tool, JsonNode args) {
        return validator.validate(tool.spec().argsType(), args);
    }

    /** v0.0.18 🍊 Permitted tools with their descriptions (main consciousness). */
    @Override
    public String permittedTools(PermissionScope scope) {
        String text = index().values().stream().filter(t -> permitted(t, scope))
                .map(t -> "- " + t.spec().name() + ": " + t.spec().description())
                .collect(Collectors.joining("\n"));
        return text.isEmpty() ? "(no tools available)" : text;
    }

    /** v0.0.18 🍊 Every tool with its required permissions and argument schema (tool-calling module). */
    @Override
    public String allTools() {
        return index().values().stream()
                .map(t -> "- " + t.spec().name() + " — " + t.spec().description()
                        + "\n  requires: " + t.spec().permissions().stream().map(Enum::name).sorted()
                        .collect(Collectors.joining(", "))
                        + "\n  arguments (JSON schema): " + schemas.schemaText(t.spec().argsType()))
                .collect(Collectors.joining("\n"));
    }

    /** v0.0.18 🍊 Name index, sorted (built once; tools are singletons). */
    private Map<String, Tool<?>> index() {
        Map<String, Tool<?>> current = byName;
        if (current == null) {
            TreeMap<String, Tool<?>> built = new TreeMap<>();
            tools.orderedStream().forEach(t -> built.put(t.spec().name(), t));
            current = Collections.unmodifiableMap(built);
            byName = current;
        }
        return current;
    }
}
