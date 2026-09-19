package ai.yuzu.sim.email;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;

import java.time.Instant;
import java.util.List;

/** v0.0.11 🍊 One simulated e-mail in an agent's mailbox (inbound customer mail or an outbound send attempt). */
public record Email(String id, AgentId agentId, long seq, Direction direction, String from, List<String> to,
                    String subject, String body, Status status, Instant createdAt) {

    /** v0.0.11 🍊 IN = received by the agent, OUT = sent (or attempted) by the agent. */
    public enum Direction { IN, OUT }

    /** v0.0.11 🍊 RECEIVED for inbound mail, SENT for delivered outbound mail, BLOCKED when a guard refused it. */
    public enum Status { RECEIVED, SENT, BLOCKED }

    /** v0.0.11 🍊 Copies the recipient list so the record is immutable. */
    public Email {
        to = List.copyOf(to);
    }

    /** v0.0.11 🍊 A not-yet-stored e-mail (id and seq are assigned by the repository). */
    static Email draft(AgentId agentId, Direction direction, String from, List<String> to, String subject, String body,
                       Status status, Instant createdAt) {
        return new Email(null, agentId, 0, direction, from, to, subject, body, status, createdAt);
    }

    /** v0.0.11 🍊 Copy carrying the id and sequence number assigned on insert. */
    Email stored(String newId, long newSeq) {
        return new Email(newId, agentId, newSeq, direction, from, to, subject, body, status, createdAt);
    }

    /** v0.0.11 🍊 Recipients as one comma-separated line ("a@acme.test, b@example.com"). */
    public String toLine() {
        return String.join(", ", to);
    }

    /** v0.0.11 🍊 API shape (contract type {@code Email}) with a natural-language time. */
    public EmailView toView(NaturalTime time) {
        return new EmailView(id, agentId.value(), direction, from, toLine(), subject, body, status,
                time.compact(createdAt));
    }
}
