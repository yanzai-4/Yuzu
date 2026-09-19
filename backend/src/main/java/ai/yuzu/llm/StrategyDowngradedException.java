package ai.yuzu.llm;

import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.structured.OutputStrategy;

/**
 * v0.0.8 🍊 The provider rejected the requested JSON format; the registry already downgraded the strategy.
 *
 * <p>The structured caller catches it, rebuilds the prompt for the weaker strategy and resends. It never
 * counts as one of the three format retries.</p>
 */
public class StrategyDowngradedException extends YuzuException {

    private final OutputStrategy next;

    /** v0.0.8 🍊 Creates the signal carrying the strategy to use next. */
    public StrategyDowngradedException(OutputStrategy next, Throwable cause) {
        super(ErrorCode.LLM_TRANSPORT, "Response format not supported; switching to " + next + ".", cause);
        this.next = next;
    }

    /** v0.0.8 🍊 Strategy to use for the next attempt. */
    public OutputStrategy next() {
        return next;
    }
}
