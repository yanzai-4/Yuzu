package ai.yuzu.tool.spi;

import ai.yuzu.agent.Permission;

import java.time.Duration;
import java.util.Set;

/**
 * v0.0.18 🍊 Static description of a tool.
 *
 * @param name        unique snake_case name used by the tool-calling module
 * @param description what the tool does (shown to the models)
 * @param argsType    record type of the arguments (strict JSON schema + validation)
 * @param permissions permissions the agent must hold (checked in code)
 * @param risk        risk class
 * @param timeout     maximum execution time
 * @param trusted     true when the result is produced purely by our own code (skips the AI outbound review)
 * @param source      first-person source of the result ("I saw on the internet", "I read in my files", ...)
 */
public record ToolSpec<A>(String name, String description, Class<A> argsType, Set<Permission> permissions,
                          Risk risk, Duration timeout, boolean trusted, String source) {
}
