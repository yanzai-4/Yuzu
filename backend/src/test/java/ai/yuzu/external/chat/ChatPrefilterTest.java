package ai.yuzu.external.chat;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.PermissionScope;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.MessageKind;
import ai.yuzu.chat.StreamState;
import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.34 🍊 The code prefilter: intake belongs to the project manager, and nobody evaluates closed chatter. */
class ChatPrefilterTest {

    private static final String ROOM = "room-0001";
    private static final ChatPrefilter.RoomContext ROOM_WITH_PM = new ChatPrefilter.RoomContext(true, null);
    private final AgentProfile pm = agent("agent-1111", "Yuzu", Role.PROJECT_MANAGER);
    private final AgentProfile engineer = agent("agent-2222", "Kumquat", Role.ENGINEER);
    private final AgentProfile researcher = agent("agent-3333", "Lime", Role.RESEARCHER);

    /** v0.0.34 🍊 A human request with no @mention reaches the PM only; the others see context. */
    @Test
    void humanRequestWithoutMentionGoesToTheProjectManagerOnly() {
        ChatMessage message = human("help me do some research about iPhone Duo", List.of(), false);
        assertThat(ChatPrefilter.classify(message, pm, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
        assertThat(ChatPrefilter.classify(message, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);
        assertThat(ChatPrefilter.classify(message, researcher, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);
    }

    /** v0.0.34 🍊 A human @mention makes the message that coworker's own business. */
    @Test
    void humanMentionReachesTheMentionedCoworker() {
        ChatMessage message = human("@Lime please compare the prices", List.of(researcher.agentId().value()), false);
        assertThat(ChatPrefilter.classify(message, researcher, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
        assertThat(ChatPrefilter.classify(message, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);
        // The PM still sees every request, so it can keep intake and assignment consistent.
        assertThat(ChatPrefilter.classify(message, pm, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
    }

    /** v0.0.34 🍊 @all is addressed to everyone (the loop guard caps how many answer). */
    @Test
    void mentionAllReachesEveryone() {
        ChatMessage message = human("@all standup in five minutes", List.of(), true);
        for (AgentProfile agent : List.of(pm, engineer, researcher)) {
            assertThat(ChatPrefilter.classify(message, agent, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
        }
    }

    /** v0.0.34 🍊 Without any agent that may assign work, everybody evaluates: a request is never dropped. */
    @Test
    void withoutAProjectManagerEverybodyEvaluates() {
        ChatMessage message = human("help me do some research about iPhone Duo", List.of(), false);
        assertThat(ChatPrefilter.classify(message, engineer, false, new ChatPrefilter.RoomContext(false, null))).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
    }

    /** v0.0.34 🍊 A human answering the coworker that just spoke reaches it, even without an @mention. */
    @Test
    void theAnswerToTheCoworkerThatJustSpokeReachesIt() {
        ChatMessage message = human("the earnings calls, please", List.of(), false);
        ChatPrefilter.RoomContext afterLime = new ChatPrefilter.RoomContext(true, researcher.agentId().value());
        assertThat(ChatPrefilter.classify(message, researcher, false, afterLime)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
        assertThat(ChatPrefilter.classify(message, engineer, false, afterLime)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);
    }

    /** v0.0.34 🍊 A human writing the coworker's name without the @ still reaches it (but not lookalikes). */
    @Test
    void aPlainNameReachesTheCoworker() {
        ChatMessage byName = human("Lime, can you prioritise the earnings calls?", List.of(), false);
        assertThat(ChatPrefilter.classify(byName, researcher, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
        assertThat(ChatPrefilter.classify(byName, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);

        ChatMessage lookalike = human("the limestone report is ready", List.of(), false);
        assertThat(ChatPrefilter.classify(lookalike, researcher, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);
    }

    /** v0.0.34 🍊 Coworker traffic is unchanged: own posts drop, closed or deep chains stay context. */
    @Test
    void coworkerTrafficIsUnchanged() {
        ChatMessage own = new ChatMessage("msg-1", ROOM, 1, AuthorKind.AGENT, engineer.agentId().value(), "Kumquat",
                MessageKind.TEXT, "mine", List.of(), false, false, 1, null, null, true, StreamState.NONE, null,
                Instant.now());
        assertThat(ChatPrefilter.classify(own, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.DROP);

        ChatMessage closed = new ChatMessage("msg-2", ROOM, 2, AuthorKind.AGENT, pm.agentId().value(), "Yuzu",
                MessageKind.TEXT, "@Kumquat got it", List.of(engineer.agentId().value()), false, true, 1, null, null,
                true, StreamState.NONE, null, Instant.now());
        assertThat(ChatPrefilter.classify(closed, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.CONTEXT_ONLY);

        ChatMessage open = new ChatMessage("msg-3", ROOM, 3, AuthorKind.AGENT, pm.agentId().value(), "Yuzu",
                MessageKind.TEXT, "@Kumquat can you build it?", List.of(engineer.agentId().value()), false, false, 1,
                null, null, true, StreamState.NONE, null, Instant.now());
        assertThat(ChatPrefilter.classify(open, engineer, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.EVALUATE);
    }

    /** v0.0.34 🍊 Messages nobody may evaluate (cards, warnings, system) are dropped for everyone. */
    @Test
    void messagesWithoutFanoutAreDropped() {
        ChatMessage card = new ChatMessage("msg-4", ROOM, 4, AuthorKind.AGENT, pm.agentId().value(), "Yuzu",
                MessageKind.QUESTION_CARD, "Which tagline?", List.of(), false, true, 1, null, "card-1", false,
                StreamState.NONE, null, Instant.now());
        for (AgentProfile agent : List.of(pm, engineer, researcher)) {
            assertThat(ChatPrefilter.classify(card, agent, false, ROOM_WITH_PM)).isEqualTo(ChatPrefilter.Verdict.DROP);
        }
    }

    private static ChatMessage human(String content, List<String> mentions, boolean mentionAll) {
        return new ChatMessage("msg-0000-1", ROOM, 10, AuthorKind.HUMAN, "user-abcd", "Alice", MessageKind.TEXT,
                content, mentions, mentionAll, false, 0, null, null, true, StreamState.NONE, null, Instant.now());
    }

    private static AgentProfile agent(String id, String name, Role role) {
        PermissionScope scope = new PermissionScope(EnumSet.copyOf(role.defaultScope().permissions()),
                Limits.defaults());
        return new AgentProfile(AgentId.of(id), ROOM, name, name.toLowerCase(), "#f97316", role, role.title(),
                role.scopeText(), role.persona(), scope, AgentProfile.State.ACTIVE, 0, Instant.now(), Instant.now());
    }
}
