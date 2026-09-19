package ai.yuzu.internal.cognition;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.memory.HabitMemoryRepository;
import ai.yuzu.internal.memory.StoredMemory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * v0.0.28 🍊 The habit index cognition puts into its prompt: one line per active habit (name + when it applies).
 *
 * <p>Rendering is deterministic and cached per agent, because the text is part of a cache-stable prompt prefix.
 * The learning module invalidates an agent's entry inside the same call that writes habit memory, so the next
 * cognition pass always sees a habit the moment it exists. An empty index means the agent has learned nothing
 * yet, and cognition skips its model call entirely.</p>
 */
@Service
public class HabitIndexService {

    /** v0.0.28 🍊 Text used when the agent has no habits at all. */
    public static final String EMPTY = "(no habits yet)";

    private final HabitMemoryRepository repository;
    private final Cache<AgentId, String> cache = Caffeine.newBuilder().maximumSize(256).build();

    /** v0.0.28 🍊 Injects the habit repository. */
    public HabitIndexService(HabitMemoryRepository repository) {
        this.repository = repository;
    }

    /** v0.0.28 🍊 The index text of an agent (cached until the learning module writes). */
    public String index(AgentId agentId) {
        return cache.get(agentId, this::render);
    }

    /** v0.0.28 🍊 True when the agent has at least one habit (cognition skips its AI call when false). */
    public boolean isEmpty(AgentId agentId) {
        return EMPTY.equals(index(agentId));
    }

    /** v0.0.28 🍊 Drops the cached index after a write to habit memory. */
    public void invalidate(AgentId agentId) {
        cache.invalidate(agentId);
    }

    /** v0.0.28 🍊 Renders "- <id>: <name> — use it <scenario>" per active habit, oldest first. */
    private String render(AgentId agentId) {
        List<StoredMemory> habits = repository.active(agentId);
        if (habits.isEmpty()) {
            return EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        for (StoredMemory habit : habits) {
            sb.append("- ").append(habit.id()).append(": ").append(habit.title());
            if (!habit.scenario().isBlank()) {
                sb.append(" — use it ").append(habit.scenario().strip());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
