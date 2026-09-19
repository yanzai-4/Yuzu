package ai.yuzu.llm.prompt;

import ai.yuzu.llm.provider.LlmMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.0.9 🍊 Assembles modular prompts in the fixed cache-friendly order and refuses out-of-order segments.
 *
 * <p>S0/S1 become the system message; S2–S7 the user message with "## Title" headers; the current time
 * (S8) is always appended last by {@link #build(String)}. Empty segments are skipped. Because segment
 * order and rendering are deterministic, identical state renders byte-identical prefixes.</p>
 */
public final class PromptBuilder {

    private final List<PromptSegment> segments = new ArrayList<>();
    private SegmentRank last = SegmentRank.S0_HANDBOOK;

    /** v0.0.9 🍊 Starts a prompt with the handbook (S0) and the module instructions (S1). */
    public static PromptBuilder start(String handbook, String moduleInstructions) {
        return new PromptBuilder()
                .add(SegmentRank.S0_HANDBOOK, handbook)
                .add(SegmentRank.S1_MODULE, moduleInstructions);
    }

    /** v0.0.9 🍊 Adds a segment with the rank's default title. */
    public PromptBuilder add(SegmentRank rank, String text) {
        return add(rank, rank.defaultTitle(), text);
    }

    /** v0.0.9 🍊 Adds a titled segment; throws when it would break the rank order. */
    public PromptBuilder add(SegmentRank rank, String title, String text) {
        if (rank == SegmentRank.S8_TIME) {
            throw new IllegalStateException("The current time is added by build(); do not add S8 manually.");
        }
        if (rank.ordinal() < last.ordinal()) {
            throw new IllegalStateException("Prompt segment " + rank + " added after " + last
                    + "; segments must go from static to variable.");
        }
        last = rank;
        if (text != null && !text.isBlank()) {
            segments.add(new PromptSegment(rank, title, text.strip()));
        }
        return this;
    }

    /** v0.0.9 🍊 Renders the messages, appending the current time as the very last line. */
    public Prompt build(String nowText) {
        StringBuilder system = new StringBuilder();
        StringBuilder user = new StringBuilder();
        for (PromptSegment segment : segments) {
            StringBuilder target = segment.rank().isSystem() ? system : user;
            if (!target.isEmpty()) {
                target.append("\n\n");
            }
            target.append("## ").append(segment.title()).append('\n').append(segment.text());
        }
        if (!user.isEmpty()) {
            user.append("\n\n");
        }
        user.append("## ").append(SegmentRank.S8_TIME.defaultTitle()).append('\n').append(nowText);
        return new Prompt(List.of(LlmMessage.system(system.toString()), LlmMessage.user(user.toString())));
    }

    /** v0.0.9 🍊 The segments added so far (tests and token budgeting). */
    public List<PromptSegment> segments() {
        return List.copyOf(segments);
    }
}
