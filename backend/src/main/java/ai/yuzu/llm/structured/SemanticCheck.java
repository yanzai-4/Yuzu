package ai.yuzu.llm.structured;

import java.util.List;

/**
 * v0.0.10 🍊 Module-specific checks that a schema cannot express (for example "ACT needs at least one action").
 *
 * <p>Returned messages are fed back to the model as retry feedback, so write them as instructions.</p>
 */
@FunctionalInterface
public interface SemanticCheck<T> {

    /** v0.0.10 🍊 Problems with an otherwise schema-valid value (empty when fine). */
    List<String> errors(T value);

    /** v0.0.10 🍊 A check that accepts everything. */
    static <T> SemanticCheck<T> none() {
        return value -> List.of();
    }
}
