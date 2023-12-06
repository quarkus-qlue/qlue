package io.quarkus.qlue;

import java.util.Objects;

/**
 * An identifier for a step.
 * Step identifiers are a way to reference the implementation of a step.
 * Some identifiers are automatically generated (for example, when a method on an object is used),
 * and some are manual (for example, when a string name is provided).
 */
public abstract class StepId {
    private final StepId parent;
    private final int hashCode;

    StepId(final StepId parent, final int hashCode) {
        this.parent = parent;
        this.hashCode = (getClass().hashCode() * 19 + Objects.hashCode(parent)) * 19 + hashCode;
    }

    /**
     * {@return the parent step identifier, or <code>null</code> for none}
     */
    public StepId parent() {
        return parent;
    }

    /**
     * {@return <code>true</code> if this identifier has a parent identifier, or <code>false</code> if it does not}
     */
    public boolean hasParent() {
        return parent != null;
    }

    public String toString() {
        return toString(new StringBuilder()).toString();
    }

    public abstract StringBuilder toString(StringBuilder sb);

    public final boolean equals(Object other) {
        return other instanceof StepId si && equals(si);
    }

    public boolean equals(StepId other) {
        return other != null && Objects.equals(parent, other.parent);
    }

    public final int hashCode() {
        return hashCode;
    }

    protected StringBuilder prependParent(StringBuilder sb) {
        StepId parent = this.parent;
        if (parent != null) {
            parent.toString(sb).append("<-");
        }
        return sb;
    }
}
