package io.quarkus.qlue.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;

/**
 * A method which implements a step in the ordered process.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Step {

    /**
     * Only include this step if the given supplier class(es) return {@code true}.
     *
     * @return the supplier class array
     */
    Class<? extends BooleanSupplier>[] when() default {};

    /**
     * Only include this step if the given supplier class(es) return {@code false}.
     *
     * @return the supplier class array
     */
    Class<? extends BooleanSupplier>[] unless() default {};
}
