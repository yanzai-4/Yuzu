package ai.yuzu.module;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.RoomWindow;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.internal.consciousness.PoolRenderer;
import ai.yuzu.internal.memory.WorkingMemoryService;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.RoomMember;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.0.14 🍊 Renders the shared prompt pieces every module uses, deterministically (byte-stable for caching).
 *
 * <p>Roster (S2), self (S3), working memory (S5), the anchored chat window (S6) and pool batches (S7).
 * Untrusted text (chat, pool) is rendered as JSON so speakers and sources cannot be spoofed from inside a
 * message. Only absolute natural-language times are used.</p>
 */
@Component
public class ContextAssembler {

    private final RoomDirectory directory;
    private final RoomWindow window;
    private final WorkingMemoryService workingMemory;
    private final PoolRenderer poolRenderer;
    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.14 🍊 Injects collaborators. */
    public ContextAssembler(RoomDirectory directory, RoomWindow window, WorkingMemoryService workingMemory,
                            PoolRenderer poolRenderer, Jsons jsons, NaturalTime time) {
        this.directory = directory;
        this.window = window;
        this.workingMemory = workingMemory;
        this.poolRenderer = poolRenderer;
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.14 🍊 Everyone in the workgroup with role and title (humans first, then agents; creation order). */
    public String roster(String roomId) {
        StringBuilder sb = new StringBuilder();
        for (RoomMember m : directory.members(roomId)) {
            if (m.isAgent()) {
                sb.append("- ").append(m.name()).append(" (AI coworker, ").append(m.id()).append(") — ")
                        .append(m.title()).append(" [").append(m.role()).append("]\n");
            } else {
                sb.append("- ").append(m.name()).append(" (human) — human teammate; can assign and approve tasks\n");
            }
        }
        return sb.isEmpty() ? "(nobody yet)" : sb.toString().strip();
    }

    /** v0.0.14 🍊 The agent's own profile, job scope and permissions. */
    public String self(AgentProfile profile) {
        return "You are " + profile.name() + ".\n" + profile.describe();
    }

    /** v0.0.14 🍊 Working memory: digest + verbatim entries. */
    public String workingMemory(AgentId agentId) {
        return workingMemory.render(agentId);
    }

    /** v0.0.14 🍊 The anchored chat window before a message (20-29 messages) as a JSON array. */
    public String chatWindow(String roomId, ChatMessage newest) {
        return renderMessages(roomId, window.anchoredContext(roomId, newest));
    }

    /** v0.0.14 🍊 Chat messages as a JSON array of {time, from, kind, text, mentions, closed}. */
    public String renderMessages(String roomId, List<ChatMessage> messages) {
        List<Map<String, Object>> items = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            items.add(renderMessage(roomId, m));
        }
        return jsons.write(items);
    }

    /** v0.0.14 🍊 One chat message as an ordered JSON object. */
    public Map<String, Object> renderMessage(String roomId, ChatMessage m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("time", time.compact(m.createdAt()));
        item.put("from", speaker(roomId, m));
        item.put("kind", m.kind().name());
        item.put("text", m.content());
        List<String> names = new ArrayList<>();
        if (m.mentionAll()) {
            names.add("all");
        }
        for (String id : m.mentions()) {
            names.add(directory.byId(roomId, id).map(RoomMember::name).orElse(id));
        }
        item.put("mentions", names);
        if (m.closure()) {
            item.put("closed", true);
        }
        return item;
    }

    /** v0.0.14 🍊 How a speaker is shown: "Alice (human)", "Lime (AI coworker, Data Researcher)". */
    public String speaker(String roomId, ChatMessage m) {
        return switch (m.authorKind()) {
            case HUMAN -> m.authorName() + " (human)";
            case AGENT -> m.authorName() + " (AI coworker, " + directory.byId(roomId, m.authorId())
                    .map(RoomMember::title).orElse("coworker") + ")";
            case SYSTEM -> "Yuzu HQ (system)";
        };
    }

    /** v0.0.14 🍊 A pool batch as a JSON array of {from, text} (sources chosen by code). */
    public String pool(List<PoolMessage> batch) {
        return poolRenderer.render(batch);
    }

    /** v0.0.14 🍊 First-person attribution for a chat message entering the pool. */
    public String attribution(String roomId, ChatMessage m) {
        String who = m.authorKind() == AuthorKind.SYSTEM ? "The Yuzu system" : speaker(roomId, m);
        return who + " told me in the group chat at " + time.compact(m.createdAt());
    }
}
