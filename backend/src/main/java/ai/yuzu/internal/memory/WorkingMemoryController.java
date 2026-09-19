package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v0.0.13 🍊 {@code GET /api/agents/{agentId}/working-memory} for the agent inspector. */
@RestController
@RequestMapping("/api/agents")
public class WorkingMemoryController {

    private final WorkingMemoryService memory;

    /** v0.0.13 🍊 Injects the service. */
    public WorkingMemoryController(WorkingMemoryService memory) {
        this.memory = memory;
    }

    /** v0.0.13 🍊 The digest and verbatim entries of an agent. */
    @GetMapping("/{agentId}/working-memory")
    public WorkingMemoryView workingMemory(@PathVariable String agentId) {
        return memory.view(AgentId.of(agentId));
    }
}
