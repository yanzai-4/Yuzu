package ai.yuzu.external.chat;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.runtime.AgentComponentFactory;
import ai.yuzu.common.concurrent.AsyncRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

/** v0.0.15 🍊 Gives every agent runtime its own {@link ChatInbox} (handler resolved lazily). */
@Component
public class ChatInboxFactory implements AgentComponentFactory<ChatInbox> {

    private final ObjectProvider<ChatTriageService> triage;
    private final ScheduledExecutorService timer;
    private final AsyncRunner runner;

    /** v0.0.15 🍊 Injects collaborators. */
    public ChatInboxFactory(ObjectProvider<ChatTriageService> triage, ScheduledExecutorService timerExecutor,
                            AsyncRunner runner) {
        this.triage = triage;
        this.timer = timerExecutor;
        this.runner = runner;
    }

    /** v0.0.15 🍊 Component type. */
    @Override
    public Class<ChatInbox> type() {
        return ChatInbox.class;
    }

    /** v0.0.15 🍊 Creates the inbox of an agent. */
    @Override
    public ChatInbox create(AgentProfile profile) {
        return new ChatInbox(profile.agentId(), (agent, batch) -> triage.getObject().handle(agent, batch), timer,
                runner);
    }
}
