package io.quarkus.qlue.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.qlue.item.Item;

/**
 * Declare that this step comes before the given items are consumed, but using {@linkplain Weak weak semantics}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(BeforeConsumeWeak.List.class)
public @interface BeforeConsumeWeak {
    /**
     * The item type whose consumption is preceded by this step.
     *
     * @return the item
     */
    Class<? extends Item> value();

    /**
     * The class argument for the item, if any.
     *
     * @return the argument
     */
    Class<?> forClass() default None.class;

    /**
     * The repeatable holder for {@link BeforeConsumeWeak}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        /**
         * The {@link BeforeConsumeWeak} instances.
         *
         * @return the instances
         */
        BeforeConsumeWeak[] value();
    }
}