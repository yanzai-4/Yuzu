package ai.yuzu.workspace;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.UpdateAgentRequest;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.config.YuzuProperties;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.11 🍊 Spring wiring: workspaces live under yuzu.workspace-root and the quota follows the agent's limits. */
@IntegrationTest
class WorkspaceIntegrationTest {

    @Autowired
    private WorkspaceService workspaces;

    @Autowired
    private AgentService agents;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private YuzuProperties properties;

    /** v0.0.11 🍊 fileQuotaMb is enforced and an edited limit applies to the next write. */
    @Test
    void quotaFollowsTheAgentsLimits() throws IOException {
        String room = TestRooms.create(jdbc);
        AgentProfile agent = agents.create(room, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null,
                new Limits.LimitsPatch(null, null, null, null, 1)));
        AgentWorkspace workspace = workspaces.forAgent(agent.agentId());
        try {
            assertThat(workspace.root()).isEqualTo(properties.workspaceRootPath().resolve(agent.agentId().value()));
            assertThat(workspace.quotaBytes()).isEqualTo(1L << 20);
            workspace.writeText("files/a.txt", "a".repeat(700_000));
            assertThatThrownBy(() -> workspace.writeText("files/b.txt", "b".repeat(700_000)))
                    .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("quota");

            agents.update(agent.agentId(), new UpdateAgentRequest(null, null, null, null,
                    new Limits.LimitsPatch(null, null, null, null, 5)));
            assertThat(workspace.quotaBytes()).isEqualTo(5L << 20);
            workspace.writeText("files/b.txt", "b".repeat(700_000));
            assertThat(workspaces.forAgent(agent.agentId())).isSameAs(workspace);
        } finally {
            FileSystemUtils.deleteRecursively(workspace.root());
        }
    }

    /** v0.0.11 🍊 An id without an agent row gets the default 50 MB quota. */
    @Test
    void unknownAgentsGetTheDefaultQuota() throws IOException {
        AgentWorkspace workspace = workspaces.forAgent(IdGen.newAgentId());
        try {
            assertThat(workspace.quotaBytes()).isEqualTo((long) Limits.defaults().fileQuotaMb() << 20);
        } finally {
            FileSystemUtils.deleteRecursively(workspace.root());
        }
    }
}
