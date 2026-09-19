package ai.yuzu.persistence;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.function.Consumer;

/**
 * v0.0.2 🍊 Base class of every repository that stores agent-internal data.
 *
 * <p>Isolation is enforced twice: {@link #scoped(String, AgentId)} refuses SQL that does not bind
 * {@code :agentId}, and a unit test scans every {@code SQL_*} constant of subclasses for {@code agent_id}.
 * Record ids follow {@code <name>-<agentHex>-<10hex>}; {@link #insertWithFreshId} retries on the (rare)
 * random-id collision.</p>
 */
public abstract class AgentScopedRepository {

    private static final int MAX_ID_ATTEMPTS = 3;

    protected final JdbcClient jdbc;

    /** v0.0.2 🍊 Creates the repository over the shared JdbcClient. */
    protected AgentScopedRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** v0.0.2 🍊 Starts a statement that is always bound to the owning agent via :agentId. */
    protected JdbcClient.StatementSpec scoped(String sql, AgentId agentId) {
        if (!sql.contains(":agentId")) {
            throw new IllegalStateException("Agent-scoped SQL must bind :agentId -> " + sql);
        }
        return jdbc.sql(sql).param("agentId", agentId.value());
    }

    /**
     * v0.0.2 🍊 Runs an insert with a freshly generated record id, retrying on a duplicate id.
     *
     * @return the id that was inserted
     */
    protected String insertWithFreshId(DataName dataName, AgentId owner, Consumer<String> insert) {
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = IdGen.recordId(dataName, owner);
            try {
                insert.accept(id);
                return id;
            } catch (DuplicateKeyException e) {
                if (!String.valueOf(e.getMessage()).contains("uk_id")) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }
}
