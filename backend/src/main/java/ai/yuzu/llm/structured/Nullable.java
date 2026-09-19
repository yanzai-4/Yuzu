package ai.yuzu.llm.structured;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * v0.0.10 🍊 Marks a record component of an LLM output as optional: the strict schema allows {@code null}.
 *
 * <p>Strict structured outputs require every property; optionality is expressed as {@code ["type","null"]}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
public @interface Nullable {
}
