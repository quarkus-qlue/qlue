package io.quarkus.qlue;

import io.smallrye.common.constraint.Assert;

/**
 * A step identifier which refers to a particular object instance.
 */
public final class InstanceStepId extends StepId {
    private final Object instance;

    /**
     * Construct a new instance.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param instance the object instance (must not be {@code null})
     */
    public InstanceStepId(final StepId parent, final Object instance) {
        super(parent, System.identityHashCode(instance));
        this.instance = Assert.checkNotNullParam("instance", instance);
    }

    /**
     * Construct a new instance.
     *
     * @param instance the object instance (must not be {@code null})
     */
    public InstanceStepId(final Object instance) {
        this(null, instance);
    }

    /**
     * {@return the instance}
     */
    public Object instance() {
        return instance;
    }

    public boolean equals(final StepId other) {
        return other instanceof InstanceStepId si && equals(si);
    }

    public boolean equals(final InstanceStepId other) {
        return this == other || super.equals(other) && instance == other.instance;
    }

    public StringBuilder toString(final StringBuilder sb) {
        return prependParent(sb).append(instance.getClass().getName()).append('@')
                .append(Integer.toHexString(System.identityHashCode(instance)));
    }
}
