package ai.yuzu.room;

/**
 * v0.0.5 🍊 Public shape of a human user (contract type {@code User}).
 *
 * @param id       user-xxxx
 * @param username display name
 * @param color    avatar color
 * @param roomId   room the user joined
 */
public record UserView(String id, String username, String color, String roomId) {
}
