package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;

import java.util.concurrent.atomic.AtomicReference;

/**
 * v0.0.6 🍊 Container of ALL mutable runtime state of one agent; nothing outside mutates it.
 *
 * <p>Shared module logic is stateless; everything agent-specific (profile snapshot, cancellation,
 * pause flag, and later the consciousness pool, main loop, chat inbox and working memory) lives here,
 * keyed by agent id, so agents are isolated from each other and can run concurrently.</p>
 */
public final class AgentRuntime {

    private final AgentId agentId;
    private final AtomicReference<AgentProfile> profile;
    private final AtomicReference<CancelToken> cancel = new AtomicReference<>(new CancelToken());
    private volatile boolean paused;

    /** v0.0.6 🍊 Creates the runtime for a profile. */
    AgentRuntime(AgentProfile profile) {
        this.agentId = profile.agentId();
        this.profile = new AtomicReference<>(profile);
        this.paused = profile.state() == AgentProfile.State.PAUSED;
    }

    /** v0.0.6 🍊 The agent id. */
    public AgentId agentId() {
        return agentId;
    }

    /** v0.0.6 🍊 Latest profile snapshot (updated when the profile is edited). */
    public AgentProfile profile() {
        return profile.get();
    }

    /** v0.0.6 🍊 The cancellation token shared by the agent's in-flight work. */
    public CancelToken cancelToken() {
        return cancel.get();
    }

    /** v0.0.6 🍊 True while paused (no chat evaluation, no main runs). */
    public boolean isPaused() {
        return paused;
    }

    /** v0.0.6 🍊 Replaces the profile snapshot after an edit. */
    void updateProfile(AgentProfile next) {
        profile.set(next);
        paused = next.state() == AgentProfile.State.PAUSED;
    }

    /** v0.0.6 🍊 Cancels everything in flight and installs a fresh token for future work. */
    public void interrupt(String reason) {
        cancel.getAndSet(new CancelToken()).cancel(reason);
    }

    /** v0.0.6 🍊 Stops the agent for good (retirement or shutdown). */
    void shutdown(String reason) {
        paused = true;
        cancel.get().cancel(reason);
    }
}
