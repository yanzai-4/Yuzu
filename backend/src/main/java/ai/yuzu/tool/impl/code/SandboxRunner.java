package ai.yuzu.tool.impl.code;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v0.0.26 🍊 Runs generated code under macOS {@code sandbox-exec}: no network, writes only inside the agent's
 * workspace, a wall-clock limit, a cleaned environment and captured (truncated) output.
 *
 * <p>{@code sandbox-exec} is deprecated on macOS. When it is missing, nothing is executed at all and the tool
 * says so in plain words — we never silently downgrade to an unsandboxed process.</p>
 */
@Component
public class SandboxRunner {

    /** v0.0.26 🍊 The deprecated-but-present macOS sandbox binary. */
    public static final Path SANDBOX_EXEC = Path.of("/usr/bin/sandbox-exec");

    /** v0.0.26 🍊 Most characters captured from each of stdout and stderr. */
    public static final int MAX_STREAM_CHARS = 4_000;

    /** v0.0.26 🍊 Longest a script may run. */
    public static final Duration MAX_RUNTIME = Duration.ofSeconds(20);

    /** v0.0.26 🍊 True when this machine can sandbox a process. */
    public boolean available() {
        return Files.isExecutable(SANDBOX_EXEC);
    }

    /** v0.0.26 🍊 Why running is impossible right now, or empty when it is possible. */
    public Optional<String> unavailableReason(ScriptRuntime runtime) {
        if (!available()) {
            return Optional.of("sandbox-exec is not available on this machine (Apple deprecated it), and I never "
                    + "run generated code outside the sandbox");
        }
        if (runtime.interpreter().isEmpty()) {
            return Optional.of("I could not find a " + runtime.display() + " interpreter on this machine");
        }
        return Optional.empty();
    }

    /**
     * v0.0.26 🍊 Runs one script inside the sandbox and returns its captured output.
     *
     * @param workspaceRoot the only directory the process may write to
     * @param script        absolute path of the file to run (inside the workspace)
     * @param runtime       interpreter to start
     * @param timeout       wall-clock budget (capped at {@link #MAX_RUNTIME})
     */
    public RunOutcome run(Path workspaceRoot, Path script, ScriptRuntime runtime, Duration timeout) {
        Optional<String> blocked = unavailableReason(runtime);
        if (blocked.isPresent()) {
            return RunOutcome.skipped(blocked.get());
        }
        Path interpreter = runtime.interpreter().orElseThrow();
        Duration budget = timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(MAX_RUNTIME) > 0 ? MAX_RUNTIME : timeout;
        List<String> command = new ArrayList<>(List.of(SANDBOX_EXEC.toString(), "-p",
                SandboxProfile.denyNetwork(workspaceRoot), interpreter.toString(), script.toString()));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(script.getParent().toFile());
        Map<String, String> env = builder.environment();
        env.clear();
        env.put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        env.put("HOME", workspaceRoot.toString());
        env.put("TMPDIR", workspaceRoot.toString());
        env.put("LANG", "en_US.UTF-8");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("YUZU_SANDBOX", "1");
        Instant started = Instant.now();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return RunOutcome.skipped("the sandbox could not start the process (" + e.getClass().getSimpleName() + ")");
        }
        AtomicBoolean cut = new AtomicBoolean(false);
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread outReader = reader(process.getInputStream(), out, cut);
        Thread errReader = reader(process.getErrorStream(), err, cut);
        boolean timedOut = false;
        int exit = -1;
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            // the script simply gets no stdin
        }
        try {
            if (process.waitFor(budget.toMillis(), TimeUnit.MILLISECONDS)) {
                exit = process.exitValue();
            } else {
                timedOut = true;
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
            outReader.join(2_000);
            errReader.join(2_000);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return RunOutcome.skipped("I was interrupted while the script was running");
        }
        return new RunOutcome(true, null, exit, out.toString().strip(), err.toString().strip(), timedOut, cut.get(),
                Duration.between(started, Instant.now()));
    }

    /** v0.0.26 🍊 Drains one stream on a virtual thread, keeping at most MAX_STREAM_CHARS of it. */
    private static Thread reader(InputStream stream, StringBuilder sink, AtomicBoolean cut) {
        return Thread.ofVirtual().name("sandbox-reader").start(() -> {
            char[] buffer = new char[4_096];
            try (var in = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    int room = MAX_STREAM_CHARS - sink.length();
                    if (room <= 0) {
                        cut.set(true);
                        continue;
                    }
                    sink.append(buffer, 0, Math.min(read, room));
                    if (read > room) {
                        cut.set(true);
                    }
                }
            } catch (IOException e) {
                // the process died while we were reading: whatever we have is what the agent sees
            }
        });
    }
}
