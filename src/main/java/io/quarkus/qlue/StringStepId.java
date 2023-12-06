package io.quarkus.qlue;

import io.smallrye.common.constraint.Assert;

/**
 * A simple text string step identifier.
 */
public final class StringStepId extends StepId {
    private final String string;

    /**
     * Construct a new instance.
     *
     * @param parent the parent identifier, or {@code null} for no parent
     * @param string the string value (must not be {@code null})
     */
    public StringStepId(final StepId parent, final String string) {
        super(parent, string.hashCode());
        this.string = Assert.checkNotNullParam("string", string);
    }

    /**
     * Construct a new instance.
     *
     * @param string the string value (must not be {@code null})
     */
    public StringStepId(final String string) {
        this(null, string);
    }

    public String toString() {
        return hasParent() ? super.toString() : string;
    }

    public StringBuilder toString(final StringBuilder sb) {
        return prependParent(sb).append(string);
    }

    public boolean equals(final StepId other) {
        return other instanceof StringStepId ssi && equals(ssi);
    }

    public boolean equals(final StringStepId other) {
        return this == other || super.equals(other) && string.equals(other.string);
    }
}
