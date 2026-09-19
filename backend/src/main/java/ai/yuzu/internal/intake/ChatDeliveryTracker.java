package ai.yuzu.internal.intake;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.runtime.AgentComponent;
import ai.yuzu.agent.runtime.AgentComponentFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * v0.0.16 🍊 Per-agent marker of the last chat message already delivered into the mind.
 *
 * <p>Each forward only carries chat the mind has not seen yet (plus the new messages), so the same window is
 * not copied into working memory again and again.</p>
 */
public final class ChatDeliveryTracker implements AgentComponent {

    private final AtomicLong lastSeq = new AtomicLong();

    /** v0.0.16 🍊 Sequence number of the last delivered chat message. */
    public long lastDeliveredSeq() {
        return lastSeq.get();
    }

    /** v0.0.16 🍊 Advances the marker (never moves backwards). */
    public void delivered(long seq) {
        lastSeq.accumulateAndGet(seq, Math::max);
    }

    /** v0.0.16 🍊 Creates one tracker per agent runtime. */
    @Component
    public static class Factory implements AgentComponentFactory<ChatDeliveryTracker> {

        /** v0.0.16 🍊 Component type. */
        @Override
        public Class<ChatDeliveryTracker> type() {
            return ChatDeliveryTracker.class;
        }

        /** v0.0.16 🍊 Creates the tracker. */
        @Override
        public ChatDeliveryTracker create(AgentProfile profile) {
            return new ChatDeliveryTracker();
        }
    }
}
