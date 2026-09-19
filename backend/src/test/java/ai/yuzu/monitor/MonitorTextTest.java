package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Text clipping, bubble summaries, detail sanitizing, trace ids and the code summarizer. */
class MonitorTextTest {

    private static final AgentId AGENT = AgentId.of("agent-3fa9");

    /** v0.0.12 🍊 Bubble summaries are one line, at most 80 characters, cut at a word boundary. */
    @Test
    void bubbleSummaries() {
        String summary = BubbleText.summary("Searching the web for the latest yuzu, lime and kumquat prices\n"
                + "across three wholesale markets in Asia and Europe", "fallback");
        assertThat(summary).hasSizeLessThanOrEqualTo(BubbleText.MAX_SUMMARY).endsWith("…").doesNotContain("\n")
                .startsWith("Searching the web for the latest yuzu, lime and kumquat prices across");
        assertThat(summary.charAt(summary.length() - 2)).isNotEqualTo(' ');
        assertThat(BubbleText.summary("   ", "fallback")).isEqualTo("fallback");
        assertThat(BubbleText.summary(null, "fallback")).isEqualTo("fallback");
        assertThat(BubbleText.summary("Short text", "fallback")).isEqualTo("Short text");
    }

    /** v0.0.12 🍊 Truncation keeps line breaks, marks the cut and never splits a surrogate pair. */
    @Test
    void truncationIsSafe() {
        String emoji = "🍊".repeat(600);
        String cut = TextClip.truncate(emoji, 1_000);
        assertThat(cut.length()).isLessThanOrEqualTo(1_000);
        assertThat(cut).endsWith("…");
        assertThat(new String(cut.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(cut);
        assertThat(TextClip.truncate("line one\nline two", 1_000)).isEqualTo("line one\nline two");
        assertThat(TextClip.truncate(null, 10)).isEmpty();
    }

    /** v0.0.12 🍊 Arbitrary details become bounded JSON-safe maps, lists and scalars. */
    @Test
    void detailsBecomeBoundedJsonSafeStructures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("nan", Double.NaN);
        detail.put("big", "x".repeat(5_000));
        detail.put("list", IntStream.range(0, 100).boxed().toList());
        detail.put("array", new int[]{1, 2, 3});
        detail.put("enum", DeskState.TALKING);
        detail.put("cyclic", cyclic);
        detail.put("json", mapper.readTree("{\"tool\":\"web.search\",\"args\":{\"q\":\"yuzu\"}}"));
        detail.put("optional", Optional.of("present"));
        detail.put("agent", AGENT);

        Map<String, Object> safe = DetailSanitizer.sanitize(detail);
        assertThat(safe.get("nan")).isEqualTo("NaN");
        assertThat((String) safe.get("big")).hasSize(DetailSanitizer.MAX_STRING).endsWith("…");
        assertThat((List<?>) safe.get("list")).hasSize(DetailSanitizer.MAX_ENTRIES + 1);
        assertThat(safe.get("array")).isEqualTo(List.of(1, 2, 3));
        assertThat(safe.get("enum")).isEqualTo("TALKING");
        assertThat(safe.get("json")).isEqualTo(Map.of("tool", "web.search", "args", Map.of("q", "yuzu")));
        assertThat(safe.get("optional")).isEqualTo("present");
        assertThat(safe.get("agent")).isEqualTo("agent-3fa9");
        assertThat(mapper.writeValueAsString(safe)).contains("{…}");
        assertThat(DetailSanitizer.sanitize(Map.of())).isNull();
        assertThat(DetailSanitizer.sanitize(null)).isNull();
    }

    /** v0.0.12 🍊 Trace and span ids are generated per agent and foreign ids are cleaned deterministically. */
    @Test
    void traceIds() {
        assertThat(TraceIds.newTraceId(AGENT)).matches("^trace-3fa9-[0-9a-f]{10}$");
        assertThat(TraceIds.newSpanId(null)).matches("^span-0000-[0-9a-f]{10}$");
        assertThat(TraceIds.clean(" ")).isNull();
        assertThat(TraceIds.clean("trace-3fa9-0123456789")).isEqualTo("trace-3fa9-0123456789");
        String cleaned = TraceIds.clean("a trace id with spaces that is far too long");
        assertThat(TraceIds.isValid(cleaned)).isTrue();
        assertThat(cleaned).hasSize(TraceIds.MAX_LENGTH).isEqualTo(TraceIds.clean("a trace id with spaces that is far too long"));
        assertThat(TraceIds.cleanOrNew(null, AGENT)).startsWith("trace-3fa9-");
        assertThat(TraceIds.isValid("x".repeat(33))).isFalse();
    }

    /** v0.0.12 🍊 The code summarizer prefers the board's default, then the latest START/STATE text, then idle. */
    @Test
    void codeSummarizer() {
        CodeBubbleSummarizer summarizer = new CodeBubbleSummarizer();
        assertThat(summarizer.summarize(AGENT, List.of(), "Replying to Alice")).isEqualTo("Replying to Alice");
        List<ModuleEvent> events = List.of(event(EventPhase.START, "Reading the brief"),
                event(EventPhase.STATE, "Drafting section 2"), event(EventPhase.END, "done"));
        assertThat(summarizer.summarize(AGENT, events, null)).isEqualTo("Drafting section 2");
        assertThat(summarizer.summarize(AGENT, List.of(), " ")).isEqualTo(BubbleText.IDLE_SUMMARY);
        assertThat(summarizer.summarize(AGENT, List.of(), "word ".repeat(40))).hasSizeLessThanOrEqualTo(80);
    }

    /** v0.0.12 🍊 A MAIN event for the summarizer tests. */
    private static ModuleEvent event(EventPhase phase, String text) {
        return new ModuleEvent(IdGen.recordId(DataName.EVENT, AGENT), AGENT, ModuleKind.MAIN, phase, text, null,
                null, "span-3fa9-0000000001", null, Instant.now());
    }
}
