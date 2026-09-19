package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.List;

/** v0.0.12 🍊 Plug-in point writing the short "what is this agent busy with" bubble text (register a @Primary bean to replace). */
public interface BubbleSummarizer {

    /** v0.0.12 🍊 Summarizes recent events (oldest first) in ~80 characters; returning currentDefault or null keeps the code text. */
    String summarize(AgentId agentId, List<ModuleEvent> recentEvents, String currentDefault);
}
