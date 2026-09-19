package ai.yuzu.module;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.LlmGateway;
import ai.yuzu.llm.prompt.PromptLibrary;
import ai.yuzu.llm.structured.StrictSchemaFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * v0.0.14 🍊 Dependencies every AI module needs, bundled so module constructors stay short.
 *
 * <p>The reporter is resolved lazily: the monitor's adapter when present, otherwise a logging fallback.</p>
 */
@Component
public class ModuleDeps {

    private final LlmGateway gateway;
    private final PromptLibrary prompts;
    private final StrictSchemaFactory schemas;
    private final ObjectProvider<ModuleReporter> reporters;
    private final AsyncRunner errors;
    private final NaturalTime time;
    private final ModuleReporter fallback = new LoggingModuleReporter();

    /** v0.0.14 🍊 Injects collaborators. */
    public ModuleDeps(LlmGateway gateway, PromptLibrary prompts, StrictSchemaFactory schemas,
                      ObjectProvider<ModuleReporter> reporters, AsyncRunner errors, NaturalTime time) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.schemas = schemas;
        this.reporters = reporters;
        this.errors = errors;
        this.time = time;
    }

    /** v0.0.14 🍊 The model gateway. */
    public LlmGateway gateway() {
        return gateway;
    }

    /** v0.0.14 🍊 Prompt templates. */
    public PromptLibrary prompts() {
        return prompts;
    }

    /** v0.0.14 🍊 Strict schema factory. */
    public StrictSchemaFactory schemas() {
        return schemas;
    }

    /** v0.0.14 🍊 Monitor reporter (logging fallback when the monitor is not wired). */
    public ModuleReporter reporter() {
        return reporters.getIfAvailable(() -> fallback);
    }

    /** v0.0.14 🍊 Error fan-out (log + realtime error events). */
    public AsyncRunner errors() {
        return errors;
    }

    /** v0.0.14 🍊 Natural-language time. */
    public NaturalTime time() {
        return time;
    }

    /** v0.0.14 🍊 Reporter that only logs (used before the monitor adapter is registered). */
    static final class LoggingModuleReporter implements ModuleReporter {
        private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoggingModuleReporter.class);

        @Override
        public ModuleSpan start(AgentId agentId, String module, String text, String traceId, String parentSpanId) {
            LOG.debug("[{}] {} START {}", agentId, module, text);
            return new ModuleSpan() {
                @Override
                public void state(String t) {
                    LOG.debug("[{}] {} STATE {}", agentId, module, t);
                }

                @Override
                public void detail(String key, Object value) {
                }

                @Override
                public void end(String t) {
                    LOG.debug("[{}] {} END {}", agentId, module, t);
                }

                @Override
                public void fail(Throwable error) {
                    LOG.debug("[{}] {} ERROR {}", agentId, module, error.getMessage());
                }

                @Override
                public void cancelled(String reason) {
                    LOG.debug("[{}] {} CANCELLED {}", agentId, module, reason);
                }

                @Override
                public String spanId() {
                    return null;
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
