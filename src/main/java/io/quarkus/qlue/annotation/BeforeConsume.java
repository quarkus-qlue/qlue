package io.quarkus.qlue.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.qlue.item.Item;

/**
 * Declare that this step comes before the given items are consumed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(BeforeConsume.List.class)
public @interface BeforeConsume {
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
     * The repeatable holder for {@link BeforeConsume}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        /**
         * The {@link BeforeConsume} instances.
         *
         * @return the instances
         */
        BeforeConsume[] value();
    }
}