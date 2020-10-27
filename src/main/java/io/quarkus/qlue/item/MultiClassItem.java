package io.quarkus.qlue.item;

/**
 * An item, identified by its type and the corresponding class object, that may be produced multiple times and
 * consumed as a {@code List}. {@code MultiClassItem} subclasses which implement {@code Comparable} will be returned in
 * sorted order.
 */
public abstract class MultiClassItem<U> extends ClassItem<U> {
    protected MultiClassItem() {
    }
}
