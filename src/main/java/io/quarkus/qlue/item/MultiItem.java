package io.quarkus.qlue.item;

/**
 * An item that may be produced multiple times, and consumed as a {@code List}. {@code MultiItem} subclasses
 * which implement {@code Comparable} will be returned in sorted order.
 */
public abstract class MultiItem extends Item {
    protected MultiItem() {
    }
}
