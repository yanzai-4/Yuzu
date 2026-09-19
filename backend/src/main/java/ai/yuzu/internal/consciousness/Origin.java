package ai.yuzu.internal.consciousness;

/**
 * v0.0.12 🍊 Code-assigned source of a pool message; it can never be set from model output or chat text.
 *
 * <p>SELF (the main consciousness talking to itself) and SUBCONSCIOUS render identically to the main
 * consciousness ("me (my own thought)"), so it cannot tell them apart, but the code can: SUBCONSCIOUS messages
 * never start a main run on their own and never spawn another subconscious thread.</p>
 */
public enum Origin {
    /** v0.0.12 🍊 From outside (chat, tool results, question answers), always with a first-person attribution. */
    EXTERNAL,
    /** v0.0.12 🍊 The main consciousness's own "keep thinking" message. */
    SELF,
    /** v0.0.12 🍊 Advice from a subconscious thread. */
    SUBCONSCIOUS,
    /** v0.0.12 🍊 A warning from the behavior review (actions rejected). */
    REVIEW;

    /** v0.0.12 🍊 True when a message of this origin may start a main run (everything except SUBCONSCIOUS). */
    public boolean triggers() {
        return this != SUBCONSCIOUS;
    }
}
