package ai.yuzu.sim.market;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.11 🍊 Agent-scoped access to {@code fake_portfolio} (one row per agent, optimistic {@code version}). */
@Repository
public class PortfolioRepository extends AgentScopedRepository {

    private static final String SQL_FIND =
            "SELECT agent_id, cash, positions, version, updated_at FROM fake_portfolio WHERE agent_id = :agentId";
    private static final String SQL_ENSURE = """
            INSERT INTO fake_portfolio (agent_id, cash, positions, version, updated_at)
            VALUES (:agentId, :cash, :positions, 0, :updated)
            ON DUPLICATE KEY UPDATE agent_id = agent_id""";
    private static final String SQL_UPDATE = """
            UPDATE fake_portfolio SET cash = :cash, positions = :positions, version = version + 1, updated_at = :updated
            WHERE agent_id = :agentId AND version = :version""";
    private static final TypeReference<List<StoredPosition>> POSITIONS = new TypeReference<>() {
    };

    private final Jsons jsons;

    /** v0.0.11 🍊 Positions are stored as JSON with decimal strings, so no precision is ever lost. */
    record StoredPosition(String symbol, String qty, String avgPrice) {
    }

    /** v0.0.11 🍊 Injects the JDBC client and the JSON helper. */
    public PortfolioRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.jsons = jsons;
    }

    /** v0.0.11 🍊 The stored portfolio, or empty when the agent never traded. */
    public Optional<Portfolio> find(AgentId agentId) {
        return scoped(SQL_FIND, agentId).query((rs, i) -> new Portfolio(AgentId.of(rs.getString("agent_id")),
                rs.getBigDecimal("cash"), readPositions(rs.getString("positions")), rs.getInt("version"),
                DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)))).optional();
    }

    /** v0.0.11 🍊 Creates the starting row ($10,000, no positions) unless one exists. */
    public void ensure(AgentId agentId, Instant now) {
        scoped(SQL_ENSURE, agentId).param("cash", Portfolio.STARTING_CASH).param("positions", "[]")
                .param("updated", DbTime.toDb(now)).update();
    }

    /** v0.0.11 🍊 Writes the new state only if the version is still {@code expectedVersion}; false on a conflict. */
    public boolean update(Portfolio next, int expectedVersion) {
        return scoped(SQL_UPDATE, next.agentId()).param("cash", next.cash())
                .param("positions", writePositions(next.positions())).param("updated", DbTime.toDb(next.updatedAt()))
                .param("version", expectedVersion).update() == 1;
    }

    /** v0.0.11 🍊 Positions → JSON array of decimal strings. */
    private String writePositions(List<Portfolio.Position> positions) {
        return jsons.write(positions.stream().map(p -> new StoredPosition(p.symbol(), p.qty().toPlainString(),
                p.avgPrice().toPlainString())).toList());
    }

    /** v0.0.11 🍊 JSON array → positions. */
    private List<Portfolio.Position> readPositions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return jsons.read(json, POSITIONS).stream()
                .map(p -> new Portfolio.Position(p.symbol(), new BigDecimal(p.qty()), new BigDecimal(p.avgPrice())))
                .toList();
    }
}
