package ai.yuzu.common.error;

/**
 * v0.0.31 🍊 Thrown when the configured token or cost budget is used up and model calls are paused.
 *
 * <p>Every module treats it like any other {@link YuzuException} and degrades gracefully (chat ignores,
 * the main consciousness ends its run, reviews fail closed), so eight agents stop calling the provider
 * instead of burning through the account.</p>
 */
public class BudgetExhaustedException extends YuzuException {

    /** v0.0.31 🍊 Creates the exception with the reason the budget tripped. */
    public BudgetExhaustedException(String message) {
        super(ErrorCode.BUDGET_EXHAUSTED, message);
    }
}
