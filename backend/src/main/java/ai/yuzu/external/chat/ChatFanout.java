package ai.yuzu.external.chat;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatMessageListener;
import ai.yuzu.chat.RoomWindow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.34 🍊 Delivers every new group message to the chat inbox of every present agent (after the code prefilter).
 */
@Component
public class ChatFanout implements ChatMessageListener {

    private final AgentService agents;
    private final AgentRuntimeManager runtimes;
    private final LoopGuard loopGuard;
    private final RoomWindow window;

    /** v0.0.34 🍊 Injects collaborators. */
    public ChatFanout(AgentService agents, AgentRuntimeManager runtimes, LoopGuard loopGuard, RoomWindow window) {
        this.agents = agents;
        this.runtimes = runtimes;
        this.loopGuard = loopGuard;
        this.window = window;
    }

    /** v0.0.34 🍊 Prefilters and enqueues the message for each agent; mentions are urgent. */
    @Override
    public void onMessage(ChatMessage message) {
        List<AgentProfile> present = agents.list(message.roomId());
        ChatPrefilter.RoomContext room = new ChatPrefilter.RoomContext(
                present.stream().anyMatch(ChatPrefilter::ownsIntake), previousAuthorId(message));
        for (AgentProfile agent : present) {
            String agentId = agent.agentId().value();
            ChatPrefilter.Verdict verdict = ChatPrefilter.classify(message, agent,
                    loopGuard.pairSuppressed(message, agentId), room);
            if (verdict != ChatPrefilter.Verdict.EVALUATE) {
                continue;
            }
            runtimes.find(agent.agentId()).ifPresent(runtime ->
                    runtime.component(ChatInbox.class).offer(message, message.mentions(agentId)));
        }
    }

    /** v0.0.34 🍊 Author of the message before this one: a human speaking right after a coworker answers it. */
    private String previousAuthorId(ChatMessage message) {
        List<ChatMessage> recent = window.recent(message.roomId(), 2);
        return recent.size() < 2 ? null : recent.get(recent.size() - 2).authorId();
    }
}
