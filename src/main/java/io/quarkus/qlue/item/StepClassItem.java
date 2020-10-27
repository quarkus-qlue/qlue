package io.quarkus.qlue.item;

import io.smallrye.common.constraint.Assert;

/**
 * An item which represents a class that contains zero or more steps and is instantiated by the chain
 * execution.
 */
public final class StepClassItem extends SimpleClassItem<Object> implements AutoCloseable {
    private final Object instance;

    /**
     * Construct a new instance.
     *
     * @param instance the step class instance (must not be {@code null})
     */
    public StepClassItem(final Object instance) {
        this.instance = Assert.checkNotNullParam("instance", instance);
    }

    /**
     * Get the step class instance.
     *
     * @return the step class instance
     */
    public Object getInstance() {
        return instance;
    }

    /**
     * Close the instance, if it implements {@link AutoCloseable}.
     *
     * @throws Exception if the close fails
     */
    public void close() throws Exception {
        Object instance = this.instance;
        if (instance instanceof AutoCloseable) {
            ((AutoCloseable) instance).close();
        }
    }
}
