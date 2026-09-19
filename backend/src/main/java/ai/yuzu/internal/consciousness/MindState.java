package ai.yuzu.internal.consciousness;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.runtime.AgentComponent;
import ai.yuzu.agent.runtime.AgentComponentFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** v0.0.17 🍊 Per-agent state of the main consciousness between runs (consecutive THINK streak). */
public final class MindState implements AgentComponent {

    private final AtomicInteger thinkStreak = new AtomicInteger();

    /** v0.0.17 🍊 Counts a THINK step and returns the streak length. */
    public int thought() {
        return thinkStreak.incrementAndGet();
    }

    /** v0.0.17 🍊 Resets the streak (after ACT or END). */
    public void decided() {
        thinkStreak.set(0);
    }

    /** v0.0.17 🍊 Current streak length. */
    public int streak() {
        return thinkStreak.get();
    }

    /** v0.0.17 🍊 Creates one mind state per agent. */
    @Component
    public static class Factory implements AgentComponentFactory<MindState> {

        /** v0.0.17 🍊 Component type. */
        @Override
        public Class<MindState> type() {
            return MindState.class;
        }

        /** v0.0.17 🍊 Creates the state. */
        @Override
        public MindState create(AgentProfile profile) {
            return new MindState();
        }
    }
}
