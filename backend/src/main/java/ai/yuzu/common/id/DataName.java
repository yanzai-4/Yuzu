package ai.yuzu.common.id;

/**
 * v0.0.1 🍊 Canonical data-name prefixes used in record ids ({@code <name>-<agentHex>-<10hex>}).
 *
 * <p>Keeping the prefixes in one enum prevents typos and documents every persisted record type.</p>
 */
public enum DataName {
    MESSAGE("msg"),
    CARD("card"),
    POOL("pool"),
    RUN("run"),
    BATCH("batch"),
    WORKING_MEMORY("wm"),
    WORKING_DIGEST("wmdigest"),
    HABIT("habit"),
    MEMORY("memory"),
    CONFLICT("conflict"),
    TICKET("ticket"),
    TASK_LIST("list"),
    TASK_ITEM("item"),
    TOOL_CALL("tool"),
    LLM_CALL("llmcall"),
    EVENT("event"),
    EMAIL("email"),
    TRADE("trade"),
    INCIDENT("incident");

    private final String prefix;

    DataName(String prefix) {
        this.prefix = prefix;
    }

    /** v0.0.1 🍊 The lowercase prefix written at the start of the record id. */
    public String prefix() {
        return prefix;
    }
}
