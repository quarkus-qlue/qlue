package io.quarkus.qlue;

import java.util.function.Consumer;

/**
 *
 */
final class SwitchableConsumer<T> implements Consumer<T> {
    private final String toString;
    Consumer<T> delegate;

    SwitchableConsumer(final String toString) {
        this.toString = toString;
    }

    public void accept(final T t) {
        delegate.accept(t);
    }

    void setDelegate(final Consumer<T> delegate) {
        this.delegate = delegate;
    }

    public String toString() {
        return toString;
    }
}
