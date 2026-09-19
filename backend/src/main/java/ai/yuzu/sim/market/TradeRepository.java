package ai.yuzu.sim.market;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.11 🍊 Agent-scoped access to the {@code fake_trade} table (clustered by agent_id, seq). */
@Repository
public class TradeRepository extends AgentScopedRepository {

    private static final String COLUMNS =
            "seq, id, agent_id, symbol, side, qty, price, notional, status, card_id, reason, created_at, updated_at";
    private static final String SQL_INSERT = """
            INSERT INTO fake_trade (agent_id, id, symbol, side, qty, price, notional, status, card_id, reason,
                created_at, updated_at)
            VALUES (:agentId, :id, :symbol, :side, :qty, :price, :notional, :status, :cardId, :reason, :created,
                :updated)""";
    private static final String SQL_FIND =
            "SELECT " + COLUMNS + " FROM fake_trade WHERE agent_id = :agentId AND id = :id";
    private static final String SQL_RECENT = "SELECT " + COLUMNS + " FROM fake_trade WHERE agent_id = :agentId"
            + " ORDER BY created_at DESC, seq DESC LIMIT :limit";
    private static final String SQL_DECIDE_PENDING = """
            UPDATE fake_trade SET status = :status, price = :price, notional = :notional, reason = :reason,
                updated_at = :updated
            WHERE agent_id = :agentId AND id = :id AND status = 'PENDING_APPROVAL'""";

    private static final RowMapper<Trade> MAPPER = (rs, i) -> new Trade(
            rs.getString("id"), AgentId.of(rs.getString("agent_id")), rs.getLong("seq"), rs.getString("symbol"),
            Trade.Side.valueOf(rs.getString("side")), rs.getBigDecimal("qty"), rs.getBigDecimal("price"),
            rs.getBigDecimal("notional"), Trade.Status.valueOf(rs.getString("status")), rs.getString("card_id"),
            rs.getString("reason"), DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
            DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)));

    /** v0.0.11 🍊 Injects the shared JDBC client. */
    public TradeRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.11 🍊 Stores a draft with a fresh {@code trade-<hex>-<10hex>} id; returns it with id and seq. */
    public Trade insert(Trade draft) {
        KeyHolder keys = new GeneratedKeyHolder();
        String id = insertWithFreshId(DataName.TRADE, draft.agentId(), fresh -> scoped(SQL_INSERT, draft.agentId())
                .param("id", fresh).param("symbol", draft.symbol()).param("side", draft.side().name())
                .param("qty", draft.qty()).param("price", draft.price()).param("notional", draft.notional())
                .param("status", draft.status().name()).param("cardId", draft.cardId()).param("reason", draft.reason())
                .param("created", DbTime.toDb(draft.createdAt())).param("updated", DbTime.toDb(draft.updatedAt()))
                .update(keys, "seq"));
        return draft.stored(id, keys.getKey().longValue());
    }

    /** v0.0.11 🍊 One trade of the agent (never another agent's). */
    public Optional<Trade> find(AgentId agentId, String id) {
        return scoped(SQL_FIND, agentId).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.11 🍊 The agent's latest trades, newest first. */
    public List<Trade> recent(AgentId agentId, int limit) {
        return scoped(SQL_RECENT, agentId).param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.11 🍊 Stores a decision on a trade only while it is still PENDING_APPROVAL; false if already decided. */
    public boolean decidePending(Trade decided) {
        return scoped(SQL_DECIDE_PENDING, decided.agentId()).param("id", decided.id())
                .param("status", decided.status().name()).param("price", decided.price())
                .param("notional", decided.notional()).param("reason", decided.reason())
                .param("updated", DbTime.toDb(decided.updatedAt())).update() == 1;
    }
}
