package ai.yuzu.room;

/**
 * v0.0.5 🍊 A participant of a room as seen by mention parsing and by every agent's roster.
 *
 * @param id    user-xxxx or agent-xxxx
 * @param name  display name used in @mentions (username or citrus name)
 * @param kind  HUMAN or AGENT
 * @param title job title ("Human teammate" for humans)
 * @param role  role key for agents, "HUMAN" for humans
 */
public record RoomMember(String id, String name, Kind kind, String title, String role) {

    /** v0.0.5 🍊 Member kind. */
    public enum Kind { HUMAN, AGENT }

    /** v0.0.5 🍊 True for AI coworkers. */
    public boolean isAgent() {
        return kind == Kind.AGENT;
    }
}
