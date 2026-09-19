package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.consciousness.Consciousness;

import java.util.concurrent.atomic.AtomicReference;

/**
 * v0.0.12 🍊 Container of ALL mutable runtime state of one agent; nothing outside mutates it.
 *
 * <p>Shared module logic is stateless; everything agent-specific (profile snapshot, cancellation,
 * pause flag, the consciousness pool / main loop / subconscious, and later the chat inbox and working memory) lives here,
 * keyed by agent id, so agents are isolated from each other and can run concurrently.</p>
 */
public final class AgentRuntime {

    private final AgentId agentId;
    private final AtomicReference<AgentProfile> profile;
    private final AtomicReference<CancelToken> cancel = new AtomicReference<>(new CancelToken());
    private final Consciousness consciousness;
    private volatile boolean paused;

    /** v0.0.12 🍊 Creates the runtime for a profile with its consciousness (pool, main loop, subconscious). */
    AgentRuntime(AgentProfile profile, Consciousness consciousness) {
        this.agentId = profile.agentId();
        this.profile = new AtomicReference<>(profile);
        this.paused = profile.state() == AgentProfile.State.PAUSED;
        this.consciousness = consciousness;
    }

    /** v0.0.12 🍊 The agent's consciousness (the only entry point into its pool). */
    public Consciousness consciousness() {
        return consciousness;
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

    /** v0.0.12 🍊 Replaces the profile snapshot after an edit and applies pause/resume to the main loop. */
    void updateProfile(AgentProfile next) {
        profile.set(next);
        boolean nowPaused = next.state() == AgentProfile.State.PAUSED;
        if (nowPaused != paused) {
            paused = nowPaused;
            consciousness.setPaused(nowPaused);
        }
    }

    /** v0.0.6 🍊 Cancels everything in flight and installs a fresh token for future work. */
    public void interrupt(String reason) {
        cancel.getAndSet(new CancelToken()).cancel(reason);
    }

    /** v0.0.12 🍊 Stops the agent for good (retirement or shutdown). */
    void shutdown(String reason) {
        paused = true;
        consciousness.setPaused(true);
        cancel.get().cancel(reason);
    }
}
