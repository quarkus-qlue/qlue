package io.quarkus.qlue.item;

import io.smallrye.common.constraint.Assert;

/**
 * An item which represents an instance of a class that contains zero or more steps and is instantiated by the chain
 * execution.
 * <p>
 * The type argument is {@code Object}, because there are no upper-bound restrictions on the type of the instance.
 */
public final class InstanceItem extends SimpleClassItem<Object> implements AutoCloseable {
    private final Object instance;

    /**
     * Construct a new instance.
     *
     * @param instance the step class instance (must not be {@code null})
     */
    public InstanceItem(final Object instance) {
        this.instance = Assert.checkNotNullParam("instance", instance);
    }

    /**
     * Get the step class instance.
     *
     * @return the step class instance
     */
    public Object instance() {
        return instance;
    }

    /**
     * Close the instance, if it implements {@link AutoCloseable}.
     *
     * @throws Exception if the close fails
     */
    public void close() throws Exception {
        Object instance = this.instance;
        if (instance instanceof AutoCloseable c) {
            c.close();
        }
    }
}
