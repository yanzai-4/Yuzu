package ai.yuzu.monitor;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** v0.0.12 🍊 Pure rules turning running spans and flags into the desk state, the active modules and the bubble. */
final class StatusDeriver {

    /** v0.0.12 🍊 Relevance order of running spans: declared desk priority, then module relevance, then recency. */
    static final Comparator<ActiveSpan> RELEVANCE = Comparator
            .comparingInt((ActiveSpan span) -> span.desk().priority())
            .thenComparingInt(span -> span.module().relevance())
            .thenComparingLong(ActiveSpan::tick);

    /** v0.0.12 🍊 Static helpers only. */
    private StatusDeriver() {
    }

    /** v0.0.12 🍊 The most relevant running span (it drives the bubble), or null when nothing runs. */
    static ActiveSpan focus(Collection<ActiveSpan> spans) {
        ActiveSpan best = null;
        for (ActiveSpan span : spans) {
            if (best == null || RELEVANCE.compare(span, best) > 0) {
                best = span;
            }
        }
        return best;
    }

    /** v0.0.12 🍊 Desk state by priority: ERROR > PAUSED > WAITING > TALKING > THINKING > WORKING > IDLE. */
    static DeskState state(boolean error, boolean paused, boolean waiting, ActiveSpan focus) {
        if (error) {
            return DeskState.ERROR;
        }
        if (paused) {
            return DeskState.PAUSED;
        }
        if (waiting) {
            return DeskState.WAITING;
        }
        return focus == null ? DeskState.IDLE : focus.desk();
    }

    /** v0.0.12 🍊 Distinct running modules, most relevant first (a module with several spans is listed once). */
    static List<String> activeModules(Collection<ActiveSpan> spans) {
        Map<ModuleKind, ActiveSpan> best = new EnumMap<>(ModuleKind.class);
        for (ActiveSpan span : spans) {
            best.merge(span.module(), span, (a, b) -> RELEVANCE.compare(a, b) >= 0 ? a : b);
        }
        return best.values().stream().sorted(RELEVANCE.reversed()).map(span -> span.module().name()).toList();
    }

    /** v0.0.12 🍊 Code default summary of a span: its latest START/STATE text on one line, at most 80 characters. */
    static String defaultSummary(ActiveSpan span) {
        return BubbleText.summary(span.text(), BubbleText.BUSY_SUMMARY);
    }

    /** v0.0.12 🍊 Bubble for a derived state: the failure, the pause, the waiting reason, the focus span, or idle. */
    static AgentStatusView.Bubble bubble(DeskState state, ModuleKind errorModule, String errorText, boolean waiting,
                                         String waitingReason, ActiveSpan focus, String focusSummary) {
        return switch (state) {
            case ERROR -> new AgentStatusView.Bubble(errorModule.label(),
                    BubbleText.summary(errorText, EventPhase.ERROR.defaultText()));
            case PAUSED -> new AgentStatusView.Bubble(BubbleText.PAUSED_MODULE, BubbleText.PAUSED_SUMMARY);
            case IDLE -> new AgentStatusView.Bubble(BubbleText.IDLE_MODULE, BubbleText.IDLE_SUMMARY);
            default -> waiting || focus == null
                    ? new AgentStatusView.Bubble(BubbleText.WAITING_MODULE,
                    BubbleText.summary(waitingReason, BubbleText.WAITING_SUMMARY))
                    : new AgentStatusView.Bubble(focus.module().label(), focusSummary);
        };
    }
}
