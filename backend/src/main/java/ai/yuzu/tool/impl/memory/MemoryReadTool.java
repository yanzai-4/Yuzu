package ai.yuzu.tool.impl.memory;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.common.time.TimeRangeParser;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * v0.0.19 🍊 "Recall …": searches deep memories, the archive of the agent's own past thoughts and the group chat.
 *
 * <p>Time first by code ({@link TimeRangeParser}); only an unresolvable time phrase (or a request without any
 * keywords) goes to the {@link MemoryReadModule} AI, whose range code validates again. Results carry natural
 * language times. Recalled text may contain outside content, so the tool is NOT trusted: its output passes the
 * outbound safety review like any other untrusted source.</p>
 */
@Component
public class MemoryReadTool implements Tool<MemoryReadTool.Args> {

    /** v0.0.19 🍊 Arguments. */
    public record Args(@Desc("What to recall, in plain words") String query,
                       @Nullable @Desc("Only the time expression if the recall is about a time (e.g. '5 minutes ago', 'yesterday afternoon'); null otherwise") String timePhrase,
                       @Desc("Up to 6 search keywords (names, topics); empty when recalling purely by time") List<String> keywords) {
    }

    static final int MAX_ITEM_CHARS = 500;
    static final int MAX_OUTPUT_CHARS = 8000;
    private static final Set<String> STOP = Set.of("what", "when", "where", "which", "who", "whom", "why", "how",
            "the", "and", "for", "with", "that", "this", "about", "from", "did", "does", "was", "were", "are", "you",
            "your", "our", "we", "they", "them", "said", "say", "tell", "told", "recall", "remember", "happened",
            "ago", "any", "anything", "something", "have", "has", "had", "into", "onto", "there", "their", "its",
            "minutes", "minute", "hours", "hour", "days", "day", "seconds", "yesterday", "today", "last", "past");

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("memory_read",
            "Recall things from my memories: my deep memories, my own earlier thoughts and actions, and the group chat "
                    + "history, by topic and/or time (e.g. 'what did Alice ask 5 minutes ago').",
            Args.class, Set.of(Permission.MEMORY_RECALL), Risk.LOW, Duration.ofSeconds(90), false,
            "I recalled this from my memory");

    private final RecallRepository recall;
    private final MemoryReadModule module;
    private final TimeRangeParser parser;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.19 🍊 Injects collaborators. */
    public MemoryReadTool(RecallRepository recall, MemoryReadModule module, TimeRangeParser parser,
                          PermissionGuard guard, NaturalTime time) {
        this.recall = recall;
        this.module = module;
        this.parser = parser;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.19 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.19 🍊 Resolves the time range, searches the three stores and renders the hits. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.MEMORY_RECALL);
        String query = args.query() == null ? "" : args.query().strip();
        String phrase = args.timePhrase() == null || args.timePhrase().isBlank() ? null : args.timePhrase().strip();
        Instant now = time.nowInstant();
        Optional<TimeRangeParser.Range> range = parser.parse(phrase != null ? phrase : query, now);
        boolean timeUnresolved = range.isEmpty() && (phrase != null || TimeRangeParser.mentionsTime(query));
        List<String> keywords = clean(args.keywords());
        if (keywords.isEmpty() && range.isEmpty() && !timeUnresolved) {
            keywords = words(query);
        }
        if (timeUnresolved || (keywords.isEmpty() && range.isEmpty())) {
            RecallPlan plan = module.run(ctx.agent().withParent(ctx.span().spanId()),
                    new MemoryReadModule.Input(query, phrase));
            if (keywords.isEmpty()) {
                keywords = clean(plan.keywords());
            }
            if (plan.fromTime() != null) {
                Instant from = time.parseMachine(plan.fromTime()).orElse(null);
                Instant to = time.parseMachine(plan.toTime()).orElse(null);
                if (from != null && to != null && !from.isAfter(to)) {
                    range = Optional.of(new TimeRangeParser.Range(from, to.isAfter(now) ? now : to,
                            phrase != null ? phrase : "the time I asked about"));
                }
            }
            if (keywords.isEmpty() && range.isEmpty()) {
                keywords = words(query);
            }
        }
        Instant from = range.map(TimeRangeParser.Range::from).orElse(null);
        Instant to = range.map(TimeRangeParser.Range::to).orElse(null);
        String q = String.join(" ", keywords);
        String agentRoom = ctx.agent().roomId();
        List<RecallRepository.Hit> deep = recall.deep(ctx.agent().agentId(), q, from, to, 6);
        List<RecallRepository.Hit> mind = recall.mind(ctx.agent().agentId(), q, from, to, from == null ? 8 : 20);
        List<RecallRepository.Hit> chat = recall.chat(agentRoom, q, from, to, from == null ? 8 : 30);
        return ToolResult.ok(render(range.orElse(null), keywords, deep, mind, chat), time.nowInstant());
    }

    /** v0.0.19 🍊 Renders the hits by store with natural-language times, bounded in size. */
    private String render(TimeRangeParser.Range range, List<String> keywords, List<RecallRepository.Hit> deep,
                          List<RecallRepository.Hit> mind, List<RecallRepository.Hit> chat) {
        StringBuilder sb = new StringBuilder("I searched my memory");
        if (range != null) {
            sb.append(" for ").append(range.phrase()).append(" (from ").append(time.compact(range.from()))
                    .append(" to ").append(time.compact(range.to())).append(")");
        }
        if (!keywords.isEmpty()) {
            sb.append(range == null ? " for " : " about ").append(String.join(", ", keywords));
        }
        sb.append(".\n");
        section(sb, "My deep memories", deep, h -> h.title() + " (saved " + time.compact(h.at()) + "): " + cut(h.text()));
        section(sb, "My own earlier thoughts and inputs", mind, h -> time.compact(h.at()) + " — " + h.title() + ": " + cut(h.text()));
        section(sb, "The group chat", chat, h -> time.compact(h.at()) + " — " + h.title() + ": " + cut(h.text()));
        if (deep.isEmpty() && mind.isEmpty() && chat.isEmpty()) {
            sb.append("I found nothing that matches.");
        }
        String out = sb.toString().strip();
        return out.length() > MAX_OUTPUT_CHARS ? out.substring(0, MAX_OUTPUT_CHARS) + "\n(more results were cut)" : out;
    }

    /** v0.0.19 🍊 One store's section. */
    private static void section(StringBuilder sb, String title, List<RecallRepository.Hit> hits,
                                java.util.function.Function<RecallRepository.Hit, String> line) {
        if (hits.isEmpty()) {
            return;
        }
        sb.append(title).append(":\n");
        hits.forEach(h -> sb.append("- ").append(line.apply(h)).append('\n'));
    }

    /** v0.0.19 🍊 Truncates one item. */
    private static String cut(String text) {
        String t = text == null ? "" : text.strip().replace("\n", " ");
        return t.length() > MAX_ITEM_CHARS ? t.substring(0, MAX_ITEM_CHARS) + "…" : t;
    }

    /** v0.0.19 🍊 Trimmed, distinct, non-blank keywords (max 6). */
    static List<String> clean(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String k : raw) {
            if (k != null && !k.isBlank() && out.size() < 6) {
                out.add(k.strip().replaceAll("[\"+\\-<>()~*@]", " ").strip());
            }
        }
        out.remove("");
        return List.copyOf(out);
    }

    /** v0.0.19 🍊 Fallback keywords from the query itself (words of 3+ letters, stop words removed). */
    static List<String> words(String query) {
        List<String> out = new ArrayList<>();
        for (String w : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3 && !STOP.contains(w) && !out.contains(w) && out.size() < 6) {
                out.add(w);
            }
        }
        return out;
    }
}
