package ai.yuzu.common.concurrent;

import ai.yuzu.common.error.YuzuException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** v0.0.1 🍊 Always-on error sink that writes asynchronous failures to the application log. */
@Component
public class LoggingErrorSink implements ErrorSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingErrorSink.class);

    /** v0.0.1 🍊 Logs expected failures at WARN (no stack) and unexpected ones at ERROR. */
    @Override
    public void report(String context, String agentId, Throwable error) {
        if (error instanceof YuzuException ye) {
            log.warn("[{}] {} failed: {} {}", agentId, context, ye.code(), ye.getMessage());
        } else {
            log.error("[{}] {} failed unexpectedly", agentId, context, error);
        }
    }
}
