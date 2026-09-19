package ai.yuzu.internal.subconscious;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.consciousness.PoolMessage;

import java.util.List;

/** v0.0.12 🍊 Runs one subconscious pass over the NEW pool messages (implemented by the subconscious module). */
@FunctionalInterface
public interface SubconsciousHandler {

    /** v0.0.12 🍊 Handles new messages; may append SUBCONSCIOUS advice to the pool. */
    void run(AgentId agentId, List<PoolMessage> newMessages);
}
