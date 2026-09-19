package ai.yuzu.llm.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * v0.0.9 🍊 Loads every prompt template from {@code classpath:prompts/**.md} once at startup.
 *
 * <p>Templates are plain English Markdown; names are paths without extension ("handbook",
 * "modules/chat"). A missing template fails fast.</p>
 */
@Component
public class PromptLibrary {

    private final Map<String, String> templates;

    /** v0.0.9 🍊 Reads all templates into memory. */
    public PromptLibrary() throws IOException {
        Map<String, String> loaded = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources("classpath*:prompts/**/*.md")) {
            String url = resource.getURL().toString();
            String name = url.substring(url.lastIndexOf("prompts/") + "prompts/".length(), url.length() - 3);
            loaded.put(name, resource.getContentAsString(StandardCharsets.UTF_8).strip());
        }
        this.templates = Map.copyOf(loaded);
    }

    /** v0.0.9 🍊 The company handbook (segment S0, shared by every module and agent). */
    public String handbook() {
        return get("handbook");
    }

    /** v0.0.9 🍊 A template by name; throws when it does not exist. */
    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalStateException("Missing prompt template prompts/" + name + ".md");
        }
        return template;
    }

    /** v0.0.9 🍊 Names of all loaded templates. */
    public Set<String> names() {
        return templates.keySet();
    }
}
