package ai.yuzu.monitor;

/** v0.0.12 🍊 Texts of the desk bubble: fixed labels for idle, paused and waiting, plus the ~80-character summary clip. */
public final class BubbleText {

    /** v0.0.12 🍊 Maximum length of a bubble summary. */
    public static final int MAX_SUMMARY = 80;
    /** v0.0.12 🍊 Bubble label while no module is active. */
    public static final String IDLE_MODULE = "Idle";
    /** v0.0.12 🍊 Bubble summary while no module is active. */
    public static final String IDLE_SUMMARY = "Idle — waiting for something to do.";
    /** v0.0.12 🍊 Bubble label of a paused agent. */
    public static final String PAUSED_MODULE = "Paused";
    /** v0.0.12 🍊 Bubble summary of a paused agent. */
    public static final String PAUSED_SUMMARY = "Taking a break.";
    /** v0.0.12 🍊 Bubble label while the agent waits on a human (question or approval card). */
    public static final String WAITING_MODULE = "Waiting";
    /** v0.0.12 🍊 Bubble summary while waiting when no reason was given. */
    public static final String WAITING_SUMMARY = "Waiting for a human.";
    /** v0.0.12 🍊 Bubble summary of an active span that reported no text. */
    public static final String BUSY_SUMMARY = "Working on it.";

    /** v0.0.12 🍊 Static helpers only. */
    private BubbleText() {
    }

    /** v0.0.12 🍊 One-line summary of at most 80 characters (word boundary + ellipsis); blank text becomes the fallback. */
    public static String summary(String text, String fallback) {
        String clipped = TextClip.oneLine(text, MAX_SUMMARY);
        return clipped.isEmpty() ? fallback : clipped;
    }
}
