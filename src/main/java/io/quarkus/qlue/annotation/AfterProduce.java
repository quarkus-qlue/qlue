package io.quarkus.qlue.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.qlue.item.Item;

/**
 * Declare that this step comes after the given item is produced.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(AfterProduce.List.class)
public @interface AfterProduce {
    /**
     * The item type that this comes after.
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
     * The repeatable holder for {@link AfterProduce}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        /**
         * The {@link AfterProduce} instances.
         *
         * @return the instances
         */
        AfterProduce[] value();
    }

}
