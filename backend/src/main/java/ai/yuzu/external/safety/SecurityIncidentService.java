package ai.yuzu.external.safety;

import ai.yuzu.common.security.SecretScanner;
import ai.yuzu.agent.AgentService;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.persistence.AgentScopedRepository;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * v0.0.16 🍊 Records every safety block, mask and guard denial ({@code security_incident}) and pushes it live
 * ({@code security.incident}) so it appears in the UI's trace/log.
 */
@Service
public class SecurityIncidentService extends AgentScopedRepository {

    /** v0.0.16 🍊 Where in the pipeline the incident happened. */
    public enum Stage { INBOUND, OUTBOUND, BEHAVIOR, GUARD, HIGH_RISK }

    static final String SQL_INSERT = """
            INSERT INTO security_incident (agent_id, id, stage, verdict, reasons, excerpt, trace_id, created_at)
            VALUES (:agentId, :id, :stage, :verdict, :reasons, :excerpt, :trace, :created)
            """;
    static final String SQL_RECENT = """
            SELECT id, agent_id, stage, verdict, reasons, excerpt, created_at FROM security_incident
            WHERE agent_id = :agentId ORDER BY seq DESC LIMIT :limit
            """;

    private final Jsons jsons;
    private final NaturalTime time;
    private final SseHub hub;
    private final AgentService agents;

    /** v0.0.16 🍊 Injects collaborators. */
    public SecurityIncidentService(JdbcClient jdbc, Jsons jsons, NaturalTime time, SseHub hub, AgentService agents) {
        super(jdbc);
        this.jsons = jsons;
        this.time = time;
        this.hub = hub;
        this.agents = agents;
    }

    /** v0.0.16 🍊 Stores and publishes an incident; the excerpt is redacted and truncated. */
    public IncidentView record(AgentId agentId, Stage stage, String verdict, List<String> reasons, String excerpt,
                               String traceId) {
        Instant now = time.nowInstant();
        String safeExcerpt = truncate(SecretScanner.redact(excerpt == null ? "" : excerpt), 1_000);
        String id = insertWithFreshId(DataName.INCIDENT, agentId, fresh -> scoped(SQL_INSERT, agentId)
                .param("id", fresh).param("stage", stage.name()).param("verdict", verdict)
                .param("reasons", jsons.write(reasons)).param("excerpt", safeExcerpt).param("trace", traceId)
                .param("created", DbTime.toDb(now)).update());
        IncidentView view = new IncidentView(id, agentId.value(), stage.name(), verdict, reasons, safeExcerpt,
                time.compact(now));
        String roomId = agents.find(agentId).map(p -> p.roomId()).orElse("*");
        hub.publish(roomId, EventType.SECURITY_INCIDENT, agentId.value(), view);
        return view;
    }

    /** v0.0.16 🍊 Most recent incidents of an agent. */
    public List<IncidentView> recent(AgentId agentId, int limit) {
        return scoped(SQL_RECENT, agentId).param("limit", limit).query((rs, i) -> new IncidentView(
                rs.getString("id"), rs.getString("agent_id"), rs.getString("stage"), rs.getString("verdict"),
                jsons.readStringList(rs.getString("reasons")), rs.getString("excerpt"),
                time.compact(DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class))))).list();
    }

    /** v0.0.16 🍊 Truncates to a maximum length. */
    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
