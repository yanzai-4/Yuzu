package ai.yuzu.llm.structured;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** v0.0.10 🍊 Description of a record component, rendered into the JSON schema the model sees. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
public @interface Desc {

    /** v0.0.10 🍊 The description text. */
    String value();
}
