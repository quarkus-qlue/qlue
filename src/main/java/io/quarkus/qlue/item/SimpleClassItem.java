package io.quarkus.qlue.item;

/**
 * A single-valued item which is identified by its type and the corresponding class object.
 */
public abstract class SimpleClassItem<U> extends ClassItem<U> {
    protected SimpleClassItem() {
    }
}
