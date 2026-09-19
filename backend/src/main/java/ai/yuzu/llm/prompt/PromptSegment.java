package ai.yuzu.llm.prompt;

/**
 * v0.0.9 🍊 One titled block of a prompt.
 *
 * @param rank  position class (enforces the cache-friendly order)
 * @param title heading rendered as "## title"
 * @param text  body text (already rendered, byte-stable for identical inputs)
 */
public record PromptSegment(SegmentRank rank, String title, String text) {
}
