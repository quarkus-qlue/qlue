package io.quarkus.qlue.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.qlue.item.ClassItem;

/**
 * Specify the class argument for item parameters and return values that are {@link ClassItem} items. Applies to the
 * return value if specified on a method.
 */
@Target({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ForClass {
    /**
     * The class argument value.
     *
     * @return the class argument value
     */
    Class<?> value();
}
