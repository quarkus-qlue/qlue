package io.quarkus.qlue;

/**
 */
final class Consume {
    private final StepBuilder stepBuilder;
    private final ItemId itemId;
    private final Constraint constraint;
    private final ConsumeFlags flags;

    Consume(final StepBuilder stepBuilder, final ItemId itemId, final Constraint constraint, final ConsumeFlags flags) {
        this.stepBuilder = stepBuilder;
        this.itemId = itemId;
        this.constraint = constraint;
        this.flags = flags;
    }

    ConsumeFlags getFlags() {
        return flags;
    }

    Consume combine(final Constraint constraint, final ConsumeFlags flags) {
        final Constraint outputConstraint = constraint == Constraint.REAL || this.constraint == Constraint.REAL
                ? Constraint.REAL
                : Constraint.ORDER_ONLY;
        final ConsumeFlags outputFlags = !flags.contains(ConsumeFlag.OPTIONAL) || !this.flags.contains(ConsumeFlag.OPTIONAL)
                ? flags.with(this.flags).without(ConsumeFlag.OPTIONAL)
                : flags.with(this.flags);
        return new Consume(stepBuilder, itemId, outputConstraint, outputFlags);
    }

    Constraint getConstraint() {
        return constraint;
    }
}
