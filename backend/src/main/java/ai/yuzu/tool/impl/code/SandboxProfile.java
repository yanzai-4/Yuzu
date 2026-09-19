package ai.yuzu.tool.impl.code;

import java.nio.file.Path;

/**
 * v0.0.26 🍊 The {@code sandbox-exec} profile generated code runs under: deny by default, no network at all,
 * writes only inside the agent's own workspace.
 */
public final class SandboxProfile {

    /** v0.0.26 🍊 Static helpers only. */
    private SandboxProfile() {
    }

    /** v0.0.26 🍊 Profile text for one workspace: reads are allowed, writes only under the root, network denied. */
    public static String denyNetwork(Path writableRoot) {
        String root = quote(writableRoot.toAbsolutePath().normalize().toString());
        return """
                (version 1)
                (deny default)
                (deny network*)
                (allow process-exec)
                (allow process-fork)
                (allow sysctl-read)
                (allow mach-lookup)
                (allow signal (target self))
                (allow file-read*)
                (allow file-write-data (literal "/dev/null") (literal "/dev/dtracehelper"))
                (allow file-ioctl (literal "/dev/null") (literal "/dev/tty"))
                (allow file-write* (subpath %ROOT%))
                (allow file-read* (subpath %ROOT%))
                """.replace("%ROOT%", root);
    }

    /** v0.0.26 🍊 A path as a quoted sandbox literal (backslashes and quotes escaped). */
    private static String quote(String path) {
        return '"' + path.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
