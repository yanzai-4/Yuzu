package ai.yuzu.chat;

import ai.yuzu.room.RoomMember;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.5 🍊 Verifies mention parsing: multi-word names, @all, e-mail addresses, boundaries. */
class MentionParserTest {

    private final List<RoomMember> members = List.of(
            new RoomMember("user-0a0a", "Alice", RoomMember.Kind.HUMAN, "Human teammate", "HUMAN"),
            new RoomMember("agent-1111", "Blood Orange", RoomMember.Kind.AGENT, "Engineer", "ENGINEER"),
            new RoomMember("agent-2222", "Blood", RoomMember.Kind.AGENT, "Researcher", "RESEARCHER"),
            new RoomMember("agent-3333", "Yuzu", RoomMember.Kind.AGENT, "PM", "PROJECT_MANAGER"));

    /** v0.0.5 🍊 The longest name wins and order of appearance is kept. */
    @Test
    void longestNameWins() {
        var m = MentionParser.parse("@Yuzu please ask @blood orange and @Blood.", members);
        assertThat(m.memberIds()).containsExactly("agent-3333", "agent-1111", "agent-2222");
        assertThat(m.all()).isFalse();
    }

    /** v0.0.5 🍊 @all is detected; e-mail addresses and partial words are ignored. */
    @Test
    void allAndEmails() {
        var m = MentionParser.parse("@all mail client@acme.test and @Yuzuland not @Alice!", members);
        assertThat(m.all()).isTrue();
        assertThat(m.memberIds()).containsExactly("user-0a0a");
    }

    /** v0.0.5 🍊 Text without @ is cheap and empty. */
    @Test
    void noMentions() {
        var m = MentionParser.parse("hello team", members);
        assertThat(m.memberIds()).isEmpty();
        assertThat(m.all()).isFalse();
    }
}
