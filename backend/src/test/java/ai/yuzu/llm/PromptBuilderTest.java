package ai.yuzu.llm;

import ai.yuzu.llm.prompt.Prompt;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.PromptLibrary;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.prompt.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.9 🍊 Prompt ordering, byte-stable prefixes, time-last rule, handbook size and token budgeting. */
class PromptBuilderTest {

    private final TokenEstimator tokens = new TokenEstimator();

    /** v0.0.9 🍊 Different stimuli and times keep the system message and the user prefix identical. */
    @Test
    void prefixIsByteStable() {
        Prompt a = prompt("Alice asked for a report.", "Saturday, September 19, 2026 at 11:32:05 AM PDT");
        Prompt b = prompt("Bob asked something else entirely.", "Saturday, September 19, 2026 at 11:40:59 AM PDT");
        assertThat(a.messages().getFirst()).isEqualTo(b.messages().getFirst());
        String ua = a.messages().get(1).content();
        String ub = b.messages().get(1).content();
        String sharedPrefix = ua.substring(0, ua.indexOf("## New input"));
        assertThat(ub).startsWith(sharedPrefix);
        assertThat(ua).endsWith("## Current time\nSaturday, September 19, 2026 at 11:32:05 AM PDT");
    }

    /** v0.0.9 🍊 Adding a more static segment after a variable one is rejected. */
    @Test
    void orderIsEnforced() {
        PromptBuilder builder = PromptBuilder.start("handbook", "module").add(SegmentRank.S6_CHAT, "chat");
        assertThatThrownBy(() -> builder.add(SegmentRank.S2_ROSTER, "roster")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> builder.add(SegmentRank.S8_TIME, "now")).isInstanceOf(IllegalStateException.class);
    }

    /** v0.0.9 🍊 Retries append at the end, leaving the original messages untouched. */
    @Test
    void retryAppends() {
        Prompt base = prompt("x", "t");
        Prompt retry = base.withRetry("{bad json", "Your reply was invalid: ...");
        assertThat(retry.messages().subList(0, 2)).isEqualTo(base.messages());
        assertThat(retry.messages()).hasSize(4);
        assertThat(retry.messages().get(2).role()).isEqualTo("assistant");
    }

    /** v0.0.9 🍊 The handbook is long enough (>= 1024 tokens) for OpenAI prompt caching to apply. */
    @Test
    void handbookEnablesCaching() throws Exception {
        PromptLibrary library = new PromptLibrary();
        assertThat(tokens.count(library.handbook())).isGreaterThanOrEqualTo(1024);
    }

    /** v0.0.9 🍊 Tail/head trimming respects the budget. */
    @Test
    void trimming() {
        String text = "word ".repeat(2_000);
        assertThat(tokens.count(tokens.keepTail(text, 100))).isLessThanOrEqualTo(102);
        assertThat(tokens.count(tokens.keepHead(text, 100))).isLessThanOrEqualTo(102);
    }

    private Prompt prompt(String stimulus, String now) {
        return PromptBuilder.start("HANDBOOK TEXT", "MODULE TEXT")
                .add(SegmentRank.S2_ROSTER, "Alice (human)\nYuzu (PM)")
                .add(SegmentRank.S3_SELF, "Name: Lime")
                .add(SegmentRank.S5_WORKING_MEMORY, "")
                .add(SegmentRank.S7_STIMULUS, stimulus)
                .build(now);
    }
}
