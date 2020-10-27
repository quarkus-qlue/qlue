package io.quarkus.qlue.item;

/**
 * An item whose identity is comprised of the item class itself along with a {@code Class} object. Each identity
 * is treated as a distinct source or sink.
 *
 * @param <U> the upper bound of the corresponding class
 */
public abstract class ClassItem<U> extends Item {
    ClassItem() {
    }
}
