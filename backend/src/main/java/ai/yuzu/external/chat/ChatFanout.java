package ai.yuzu.external.chat;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatMessageListener;
import org.springframework.stereotype.Component;

/**
 * v0.0.15 🍊 Delivers every new group message to the chat inbox of every present agent (after the code prefilter).
 */
@Component
public class ChatFanout implements ChatMessageListener {

    private final AgentService agents;
    private final AgentRuntimeManager runtimes;
    private final LoopGuard loopGuard;

    /** v0.0.15 🍊 Injects collaborators. */
    public ChatFanout(AgentService agents, AgentRuntimeManager runtimes, LoopGuard loopGuard) {
        this.agents = agents;
        this.runtimes = runtimes;
        this.loopGuard = loopGuard;
    }

    /** v0.0.15 🍊 Prefilters and enqueues the message for each agent; mentions are urgent. */
    @Override
    public void onMessage(ChatMessage message) {
        for (AgentProfile agent : agents.list(message.roomId())) {
            String agentId = agent.agentId().value();
            ChatPrefilter.Verdict verdict = ChatPrefilter.classify(message, agentId,
                    loopGuard.pairSuppressed(message, agentId));
            if (verdict != ChatPrefilter.Verdict.EVALUATE) {
                continue;
            }
            runtimes.find(agent.agentId()).ifPresent(runtime ->
                    runtime.component(ChatInbox.class).offer(message, message.mentions(agentId)));
        }
    }
}
