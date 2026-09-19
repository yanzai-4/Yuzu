package ai.yuzu.monitor;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** v0.0.12 🍊 Live desk state of every agent: derives status + bubble, publishes throttled agent.status, drives the summarizer. */
@Component
public class AgentStatusBoard {

    private static final Logger log = LoggerFactory.getLogger(AgentStatusBoard.class);
    private static final String ALL_ROOMS = "*";
    private static final long EXPIRY_SLACK_MILLIS = 20;

    private final AgentLookup lookup;
    private final SseHub hub;
    private final NaturalTime time;
    private final AsyncRunner runner;
    private final ScheduledExecutorService timer;
    private final Supplier<BubbleSummarizer> summarizer;
    private final MonitorTimings timings;
    private final Map<AgentId, AgentLiveState> agents = new ConcurrentHashMap<>();
    private final Set<AgentId> retired = ConcurrentHashMap.newKeySet();
    private final Cache<AgentId, Optional<AgentLookup.Placement>> untracked = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();
    private final ScheduledFuture<?> refresher;

    /** v0.0.12 🍊 Spring constructor: production timings; the summarizer bean is resolved lazily (a @Primary one wins). */
    @Autowired
    public AgentStatusBoard(AgentLookup lookup, SseHub hub, NaturalTime time, AsyncRunner runner,
                            ScheduledExecutorService timerExecutor, ObjectProvider<BubbleSummarizer> summarizers) {
        this(lookup, hub, time, runner, timerExecutor, new SummarizerResolver(summarizers), MonitorTimings.DEFAULTS);
    }

    /** v0.0.12 🍊 Full constructor (tests pass their own summarizer and shorter timings). */
    AgentStatusBoard(AgentLookup lookup, SseHub hub, NaturalTime time, AsyncRunner runner,
                     ScheduledExecutorService timer, Supplier<BubbleSummarizer> summarizer, MonitorTimings timings) {
        this.lookup = lookup;
        this.hub = hub;
        this.time = time;
        this.runner = runner;
        this.timer = timer;
        this.summarizer = summarizer;
        this.timings = timings;
        long refreshMillis = timings.refreshInterval().toMillis();
        this.refresher = timer.scheduleWithFixedDelay(this::refreshAll, refreshMillis, refreshMillis,
                TimeUnit.MILLISECONDS);
    }

    /** v0.0.12 🍊 Stops the periodic status refresh on shutdown. */
    @PreDestroy
    public void close() {
        refresher.cancel(false);
    }

    /** v0.0.12 🍊 Live status of one agent (idle when the monitor does not track it, for example a retired agent). */
    public AgentStatusView status(AgentId agentId) {
        String now = time.compact(time.nowInstant());
        AgentLiveState state = tracked(agentId);
        return state == null ? AgentStatusView.idle(agentId.value(), false, now) : state.view(now, System.nanoTime());
    }

    /** v0.0.12 🍊 Live statuses of the given agents, in order (invalid ids are skipped). */
    public List<AgentStatusView> snapshot(List<String> agentIds) {
        List<AgentStatusView> statuses = new ArrayList<>(agentIds.size());
        for (String id : agentIds) {
            if (AgentId.isValid(id)) {
                statuses.add(status(AgentId.of(id)));
            }
        }
        return statuses;
    }

    /** v0.0.12 🍊 Tracks an agent (hire, boot, edit) and publishes its status; true when its pause flag changed. */
    boolean register(AgentId agentId, String roomId, boolean paused) {
        if (agentId == null || agentId.isSystem() || retired.contains(agentId)) {
            return false;
        }
        untracked.invalidate(agentId);
        boolean[] created = {false};
        AgentLiveState state = agents.computeIfAbsent(agentId, id -> {
            created[0] = true;
            return newState(id, roomId, paused);
        });
        if (retired.contains(agentId)) {
            remove(agentId);
            return false;
        }
        boolean changed = !created[0] && state.place(roomId, paused);
        state.statusTrigger().fire();
        return changed;
    }

    /** v0.0.12 🍊 Stops tracking a retired agent for good (late events of its cancelled work are ignored). */
    void remove(AgentId agentId) {
        retired.add(agentId);
        AgentLiveState state = agents.remove(agentId);
        if (state != null) {
            state.retire();
        }
    }

    /** v0.0.12 🍊 Applies an event reported by a module with the desk state its span declares (null = module default). */
    void apply(ModuleEvent event, DeskState desk) {
        AgentLiveState state = tracked(event.agentId());
        if (state != null) {
            afterChange(state, state.apply(event, desk, System.nanoTime()));
        }
    }

    /** v0.0.12 🍊 A running span declared another desk state. */
    void redesk(AgentId agentId, String spanId, ModuleKind module, DeskState desk, String text) {
        AgentLiveState state = tracked(agentId);
        if (state != null) {
            afterChange(state, state.redesk(spanId, module, desk, text));
        }
    }

    /** v0.0.12 🍊 Updates the consciousness pool size. */
    void setPoolSize(AgentId agentId, int size) {
        AgentLiveState state = tracked(agentId);
        if (state != null && state.setPoolSize(size)) {
            state.statusTrigger().fire();
        }
    }

    /** v0.0.12 🍊 Updates the number of pending action batches. */
    void setPendingBatches(AgentId agentId, int count) {
        AgentLiveState state = tracked(agentId);
        if (state != null && state.setPendingBatches(count)) {
            state.statusTrigger().fire();
        }
    }

    /** v0.0.12 🍊 Updates the waiting flag and its reason. */
    void setWaiting(AgentId agentId, boolean waiting, String reason) {
        AgentLiveState state = tracked(agentId);
        if (state != null && state.setWaiting(waiting, reason)) {
            state.statusTrigger().fire();
        }
    }

    /** v0.0.12 🍊 Updates the pause flag; true when it changed. */
    boolean setPaused(AgentId agentId, boolean paused) {
        AgentLiveState state = tracked(agentId);
        if (state != null && state.setPaused(paused)) {
            state.statusTrigger().fire();
            return true;
        }
        return false;
    }

    /** v0.0.12 🍊 Room a module event of the agent goes to: "*" for platform events, null (not streamed) when unknown. */
    String roomOf(AgentId agentId) {
        AgentLiveState state = agents.get(agentId);
        String room = state == null ? null : state.roomId();
        if (room != null) {
            return room;
        }
        if (agentId.isSystem()) {
            return ALL_ROOMS;
        }
        return placement(agentId).map(AgentLookup.Placement::roomId).orElse(null);
    }

    /** v0.0.12 🍊 State of a present agent, registered lazily from the registry; null for retired or unknown agents. */
    private AgentLiveState tracked(AgentId agentId) {
        if (agentId == null) {
            return null;
        }
        AgentLiveState state = agents.get(agentId);
        if (state != null || agentId.isSystem() || retired.contains(agentId)) {
            return state;
        }
        Optional<AgentLookup.Placement> placement = placement(agentId);
        if (placement.isEmpty() || placement.get().retired()) {
            return null;
        }
        AgentLiveState created = agents.computeIfAbsent(agentId,
                id -> newState(id, placement.get().roomId(), placement.get().paused()));
        if (retired.contains(agentId)) {
            remove(agentId);
            return null;
        }
        return created;
    }

    /** v0.0.12 🍊 Registry lookup; retired and unknown answers are cached briefly, failures are not cached. */
    private Optional<AgentLookup.Placement> placement(AgentId agentId) {
        Optional<AgentLookup.Placement> cached = untracked.getIfPresent(agentId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<AgentLookup.Placement> found = lookup.find(agentId);
            if (found.isEmpty() || found.get().retired()) {
                untracked.put(agentId, found);
            }
            return found;
        } catch (RuntimeException e) {
            log.warn("Monitor could not look up {}: {}", agentId, e.getMessage());
            return Optional.empty();
        }
    }

    /** v0.0.12 🍊 Creates the live state of an agent with its two throttled triggers. */
    private AgentLiveState newState(AgentId agentId, String roomId, boolean paused) {
        CoalescingTrigger status = new CoalescingTrigger("agent-status", agentId.value(), timings.statusInterval(),
                timer, runner, () -> publishStatus(agentId));
        CoalescingTrigger summary = new CoalescingTrigger("bubble-summary", agentId.value(),
                timings.summaryInterval(), timer, runner, () -> summarize(agentId));
        return new AgentLiveState(agentId, roomId, paused, timings, status, summary);
    }

    /** v0.0.12 🍊 Reacts to a change: publish the status, summarize while busy, schedule the end of an ERROR. */
    private void afterChange(AgentLiveState state, AgentLiveState.Effect effect) {
        if (!effect.changed()) {
            return;
        }
        state.statusTrigger().fire();
        if (effect.busy()) {
            state.summaryTrigger().fire();
        }
        if (effect.failed()) {
            try {
                timer.schedule(state.statusTrigger()::fire, timings.errorHold().toMillis() + EXPIRY_SLACK_MILLIS,
                        TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                log.debug("ERROR expiry not scheduled (shutting down)");
            }
        }
    }

    /** v0.0.12 🍊 Timer task: re-sends every status (agent.status is not replayed, so reconnected clients converge). */
    private void refreshAll() {
        try {
            for (AgentLiveState state : agents.values()) {
                state.forgetPublication();
                state.statusTrigger().fire();
            }
        } catch (Throwable t) {
            log.warn("Periodic status refresh failed", t);
        }
    }

    /** v0.0.12 🍊 Publishes the latest status of an agent unless it equals the last one published. */
    private void publishStatus(AgentId agentId) {
        AgentLiveState state = agents.get(agentId);
        if (state == null) {
            return;
        }
        AgentStatusView view = state.publication(time.compact(time.nowInstant()), System.nanoTime());
        if (view != null) {
            hub.publish(state.roomId(), EventType.AGENT_STATUS, agentId.value(), view);
        }
    }

    /** v0.0.12 🍊 Runs the summarizer off the reporting threads and pins a meaningful result. */
    private void summarize(AgentId agentId) {
        AgentLiveState state = agents.get(agentId);
        AgentLiveState.SummaryRequest request = state == null ? null : state.summaryRequest();
        if (request == null) {
            return;
        }
        String summary;
        try {
            summary = summarizer.get().summarize(agentId, request.events(), request.defaultText());
        } catch (Throwable t) {
            log.warn("Bubble summarizer failed for {}; keeping the code summary", agentId, t);
            return;
        }
        if (state.acceptSummary(request, summary)) {
            state.statusTrigger().fire();
        }
    }

    /** v0.0.12 🍊 Resolves the summarizer bean once, lazily; ambiguity (several beans, none @Primary) falls back to code. */
    private static final class SummarizerResolver implements Supplier<BubbleSummarizer> {

        private final ObjectProvider<BubbleSummarizer> provider;
        private volatile BubbleSummarizer resolved;

        /** v0.0.12 🍊 Wraps the bean provider. */
        private SummarizerResolver(ObjectProvider<BubbleSummarizer> provider) {
            this.provider = provider;
        }

        /** v0.0.12 🍊 The primary summarizer bean, or the code summarizer. */
        @Override
        public BubbleSummarizer get() {
            BubbleSummarizer current = resolved;
            if (current == null) {
                try {
                    current = provider.getIfAvailable(CodeBubbleSummarizer::new);
                } catch (BeansException e) {
                    log.warn("Several BubbleSummarizer beans and none is @Primary; using the code summarizer");
                    current = new CodeBubbleSummarizer();
                }
                resolved = current;
            }
            return current;
        }
    }
}
