package ai.yuzu.tool.impl.code;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** v0.0.26 🍊 The interpreters the sandbox may start, chosen by file extension (nothing else ever runs). */
public enum ScriptRuntime {

    // /usr/bin/python3 is only the Xcode command-line-tools shim: it refuses to run until the Xcode license
    // has been accepted, so a real interpreter is preferred over it.
    PYTHON("Python", ".py", List.of("/opt/homebrew/bin/python3", "/usr/local/bin/python3", "/usr/bin/python3")),
    BASH("Bash", ".sh", List.of("/bin/bash")),
    NODE("Node.js", ".js", List.of("/usr/local/bin/node", "/opt/homebrew/bin/node", "/usr/bin/node"));

    private final String display;
    private final String extension;
    private final List<String> candidates;

    /** v0.0.26 🍊 Declares a runtime, the extension it owns and where its interpreter may live. */
    ScriptRuntime(String display, String extension, List<String> candidates) {
        this.display = display;
        this.extension = extension;
        this.candidates = candidates;
    }

    /** v0.0.26 🍊 Human name of the runtime ("Python"). */
    public String display() {
        return display;
    }

    /** v0.0.26 🍊 The extension this runtime owns (".py"). */
    public String extension() {
        return extension;
    }

    /** v0.0.26 🍊 The runtime that can run a file name, if any. */
    public static Optional<ScriptRuntime> forFile(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(r -> lower.endsWith(r.extension)).findFirst();
    }

    /** v0.0.26 🍊 The first interpreter of this runtime that exists on this machine. */
    public Optional<Path> interpreter() {
        return candidates.stream().map(Path::of).filter(Files::isExecutable).findFirst();
    }

    /** v0.0.26 🍊 The extensions that can be run at all, for messages to the agent. */
    public static String runnableExtensions() {
        return Arrays.stream(values()).map(ScriptRuntime::extension).reduce((a, b) -> a + ", " + b).orElse("");
    }
}
