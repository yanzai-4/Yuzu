package ai.yuzu.tool.spi;

/**
 * v0.0.18 🍊 A tool ("half AI, half code"): the AI decides the arguments, code executes them.
 *
 * <p>Implementations MUST re-check their own permissions with {@link PermissionGuard} at the start of
 * {@link #execute} (defense in depth: a direct call that bypasses the dispatcher still fails).</p>
 */
public interface Tool<A> {

    /** v0.0.18 🍊 Static description. */
    ToolSpec<A> spec();

    /** v0.0.18 🍊 Executes one call with validated arguments. Risky failures throw YuzuException subclasses. */
    ToolResult execute(ToolContext ctx, A args);
}
