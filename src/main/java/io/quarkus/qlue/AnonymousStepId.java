package io.quarkus.qlue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A step identifier for anonymous steps.
 * Such steps have a unique sequence number associated with them.
 */
public final class AnonymousStepId extends StepId {
    private final long id;

    private static final AtomicLong idSeq = new AtomicLong(1);

    AnonymousStepId(final StepId parent, final long id) {
        super(parent, Long.hashCode(id));
        this.id = id;
    }

    /**
     * Construct a new instance.
     */
    public AnonymousStepId() {
        this(null, idSeq.getAndIncrement());
    }

    /**
     * {@return the unique value of this identifier}
     */
    public long id() {
        return id;
    }

    public boolean equals(final StepId other) {
        return other instanceof AnonymousStepId id && equals(id);
    }

    public boolean equals(final AnonymousStepId other) {
        return this == other || super.equals(other) && id == other.id;
    }

    public StringBuilder toString(final StringBuilder sb) {
        return sb.append("anonymous<").append(Long.toHexString(id)).append('>');
    }
}
