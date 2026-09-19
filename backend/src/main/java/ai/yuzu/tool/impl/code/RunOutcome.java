package ai.yuzu.tool.impl.code;

import java.time.Duration;

/**
 * v0.0.26 🍊 What happened when generated code was (or was not) run.
 *
 * @param ran       true when a process really started inside the sandbox
 * @param reason    why it did not run (null when it ran)
 * @param exitCode  process exit code (-1 when it did not run or was killed)
 * @param stdout    captured standard output, already truncated
 * @param stderr    captured standard error, already truncated
 * @param timedOut  true when the wall-clock budget killed the process
 * @param truncated true when output was longer than the capture limit
 * @param took      how long the process ran
 */
public record RunOutcome(boolean ran, String reason, int exitCode, String stdout, String stderr, boolean timedOut,
                         boolean truncated, Duration took) {

    /** v0.0.26 🍊 Nothing ran, and this is the reason the agent will read. */
    public static RunOutcome skipped(String reason) {
        return new RunOutcome(false, reason, -1, "", "", false, false, Duration.ZERO);
    }
}
