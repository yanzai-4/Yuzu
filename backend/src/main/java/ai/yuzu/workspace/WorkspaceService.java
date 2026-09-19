package ai.yuzu.workspace;

import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.config.YuzuProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** v0.0.11 🍊 Hands out each agent's workspace (<workspaceRoot>/agent-xxxx/), created lazily on first use. */
@Service
public class WorkspaceService {

    private final Path base;
    private final NaturalTime time;
    private final WorkspaceQuota quota;
    private final Map<AgentId, AgentWorkspace> open = new ConcurrentHashMap<>();

    /** v0.0.11 🍊 Injects the configured root, the clock and the quota source. */
    public WorkspaceService(YuzuProperties properties, NaturalTime time, WorkspaceQuota quota) {
        this.base = properties.workspaceRootPath();
        this.time = time;
        this.quota = quota;
    }

    /** v0.0.11 🍊 The agent's workspace; its folders (files, code, web, tool-outputs, llm, memory) exist afterwards. */
    public AgentWorkspace forAgent(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId");
        try {
            AgentWorkspace existing = open.get(agentId);
            if (existing != null) {
                existing.ensureTree();
                return existing;
            }
            Path root = base.resolve(agentId.value());
            AgentWorkspace.createTree(root);
            AgentWorkspace created = new AgentWorkspace(agentId, root, quota, time);
            AgentWorkspace raced = open.putIfAbsent(agentId, created);
            return raced != null ? raced : created;
        } catch (IOException e) {
            throw (ToolExecutionException) new ToolExecutionException(
                    "Could not prepare the workspace of " + agentId + ".", e)
                    .with("agentId", agentId.value()).forAgent(agentId.value());
        }
    }

    /** v0.0.11 🍊 Absolute folder that contains every agent workspace. */
    public Path basePath() {
        return base;
    }
}
