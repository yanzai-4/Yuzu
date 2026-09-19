package ai.yuzu.llm.prompt;

/**
 * v0.0.9 🍊 Fixed order of prompt segments, from the most static (shared by everyone) to the most variable.
 *
 * <p>Providers cache the longest identical prompt prefix, so the order is: handbook (identical for every
 * module and agent) → module instructions → roster → self → slow state → working memory → chat → the
 * new stimulus → the current time (changes every second, therefore always last).</p>
 */
public enum SegmentRank {
    S0_HANDBOOK("Company handbook", true),
    S1_MODULE("Your job in this step", true),
    S2_ROSTER("Team roster", false),
    S3_SELF("About you", false),
    S4_SLOW_STATE("Task state", false),
    S5_WORKING_MEMORY("Working memory", false),
    S6_CHAT("Group chat", false),
    S7_STIMULUS("New input", false),
    S8_TIME("Current time", false);

    private final String defaultTitle;
    private final boolean system;

    SegmentRank(String defaultTitle, boolean system) {
        this.defaultTitle = defaultTitle;
        this.system = system;
    }

    /** v0.0.9 🍊 Heading used when the caller gives none. */
    public String defaultTitle() {
        return defaultTitle;
    }

    /** v0.0.9 🍊 True for segments rendered into the system message (S0, S1). */
    public boolean isSystem() {
        return system;
    }
}
