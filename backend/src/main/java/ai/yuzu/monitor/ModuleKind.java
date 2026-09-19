package ai.yuzu.monitor;

/** v0.0.12 🍊 Every module that reports to the monitor (contract type ModuleKind) with its label, desk state and bubble relevance. */
public enum ModuleKind {
    /** v0.0.12 🍊 Chat triage is WORKING; a span that posts a message declares TALKING via Span.desk. */
    CHAT("Chat", DeskState.WORKING, 42),
    /** v0.0.12 🍊 Safety review of inbound content and tool results. */
    SAFETY("Safety review", DeskState.WORKING, 60),
    /** v0.0.12 🍊 Behavior review of every action batch Main decides. */
    BEHAVIOR("Behavior review", DeskState.WORKING, 65),
    /** v0.0.12 🍊 Second review of high-risk tool calls. */
    HIGH_RISK("High-risk review", DeskState.WORKING, 70),
    /** v0.0.12 🍊 Decomposes natural-language actions into tool calls. */
    TOOL_CALLING("Tool calling", DeskState.WORKING, 75),
    /** v0.0.12 🍊 The monitor itself (bubble summaries): traced but never shown on the desk. */
    MONITOR("Monitor", DeskState.IDLE, 0),
    /** v0.0.12 🍊 The single-threaded main consciousness. */
    MAIN("Main consciousness", DeskState.THINKING, 100),
    /** v0.0.12 🍊 Maintains the task list. */
    PLANNING("Planning", DeskState.THINKING, 55),
    /** v0.0.12 🍊 Picks relevant habit memories. */
    COGNITION("Cognition", DeskState.THINKING, 50),
    /** v0.0.12 🍊 Supervises Main; several threads may run at once. */
    SUBCONSCIOUS("Subconscious", DeskState.THINKING, 45),
    /** v0.0.12 🍊 Saves or modifies habit memory. */
    LEARNING("Learning", DeskState.WORKING, 40),
    /** v0.0.12 🍊 Saves or modifies deep memory. */
    MEMORY("Memory", DeskState.WORKING, 35),
    /** v0.0.12 🍊 Compacts working memory. */
    WM_COMPACTOR("Working-memory compactor", DeskState.WORKING, 30),
    /** v0.0.12 🍊 A tool executing (web, email, trade, code, file, ...). */
    TOOL("Tool", DeskState.WORKING, 80),
    /** v0.0.12 🍊 Platform events about the agent (hired, paused, retired, ...). */
    SYSTEM("System", DeskState.WORKING, 10);

    private final String label;
    private final DeskState desk;
    private final int relevance;

    /** v0.0.12 🍊 Binds the label, implied desk state and relevance. */
    ModuleKind(String label, DeskState desk, int relevance) {
        this.label = label;
        this.desk = desk;
        this.relevance = relevance;
    }

    /** v0.0.12 🍊 Human label shown in the desk bubble and the trace log (for example "Main consciousness"). */
    public String label() {
        return label;
    }

    /** v0.0.12 🍊 Desk state implied while a span of this module runs (IDLE means traced but never shown on the desk). */
    public DeskState desk() {
        return desk;
    }

    /** v0.0.12 🍊 Bubble relevance among spans with the same desk state (higher wins). */
    public int relevance() {
        return relevance;
    }

    /** v0.0.12 🍊 True for modules that are traced but never change the desk or trigger summaries (the monitor itself). */
    public boolean quiet() {
        return desk == DeskState.IDLE;
    }
}
