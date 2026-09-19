package ai.yuzu.module;

import ai.yuzu.agent.PermissionScope;

/** v0.0.17 🍊 Describes the tools an agent may use (implemented by the tool registry). */
public interface ToolCatalogProvider {

    /** v0.0.17 🍊 Deterministic text listing the tools the scope allows (for the main consciousness). */
    String permittedTools(PermissionScope scope);

    /** v0.0.17 🍊 Deterministic text listing every tool with its arguments (for the tool-calling module). */
    String allTools();

    /** v0.0.17 🍊 Fallback until the tool registry exists. */
    ToolCatalogProvider NONE = new ToolCatalogProvider() {
        @Override
        public String permittedTools(PermissionScope scope) {
            return "(no tools available)";
        }

        @Override
        public String allTools() {
            return "(no tools available)";
        }
    };
}
