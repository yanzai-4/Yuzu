package ai.yuzu.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** v0.0.5 🍊 Verifies the anchored (cache-friendly) chat context: 20-29 messages, start moves every 10. */
class RoomWindowTest {

    /** v0.0.5 🍊 Context size stays within 20-29 and its first message only changes every 10 messages. */
    @Test
    void anchoredContextIsStable() {
        ChatMessageRepository repo = mock(ChatMessageRepository.class);
        when(repo.findRecent("room-0001", RoomWindow.CAPACITY)).thenReturn(List.of());
        RoomWindow window = new RoomWindow(repo);

        List<String> firstIds = new ArrayList<>();
        for (int i = 1; i <= 260; i++) {
            ChatMessage m = message(i);
            window.append(m);
            List<ChatMessage> context = window.anchoredContext("room-0001", m);
            int prior = i - 1;
            if (prior < 20) {
                assertThat(context).hasSize(prior);
            } else {
                assertThat(context.size()).isBetween(20, 29);
                assertThat(context.getLast().seq()).isEqualTo(i - 1);
            }
            firstIds.add(context.isEmpty() ? "" : context.getFirst().id());
        }
        // Between anchor shifts (every 10 messages) the first context message is identical.
        for (int i = 31; i < 40; i++) {
            assertThat(firstIds.get(i)).isEqualTo(firstIds.get(30));
        }
        // Still stable when the 200-message window is full and sliding.
        for (int i = 241; i < 250; i++) {
            assertThat(firstIds.get(i)).isEqualTo(firstIds.get(240));
        }
    }

    /** v0.0.5 🍊 recent() returns the latest n in ascending order. */
    @Test
    void recentReturnsLatest() {
        ChatMessageRepository repo = mock(ChatMessageRepository.class);
        when(repo.findRecent("room-0001", RoomWindow.CAPACITY)).thenReturn(List.of());
        RoomWindow window = new RoomWindow(repo);
        for (int i = 1; i <= 5; i++) {
            window.append(message(i));
        }
        assertThat(window.recent("room-0001", 3)).extracting(ChatMessage::seq).containsExactly(3L, 4L, 5L);
    }

    private static ChatMessage message(int seq) {
        return new ChatMessage("msg-0000-" + String.format("%010x", seq), "room-0001", seq, AuthorKind.HUMAN,
                "user-0a0a", "Alice", MessageKind.TEXT, "m" + seq, List.of(), false, false, 0, null, null, true,
                StreamState.NONE, null, Instant.now());
    }
}
