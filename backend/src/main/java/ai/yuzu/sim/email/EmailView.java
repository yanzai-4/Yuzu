package ai.yuzu.sim.email;

/** v0.0.11 🍊 API shape of a simulated e-mail (contract type {@code Email}); {@code to} is comma-separated. */
public record EmailView(String id, String agentId, Email.Direction direction, String from, String to, String subject,
                        String body, Email.Status status, String time) {
}
