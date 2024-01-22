package io.quarkus.qlue;

/**
 *
 */
record Consume(StepBuilder stepBuilder, ItemId itemId, Constraint constraint, ConsumeFlags flags) {
    Consume {
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

    StepId stepId() {
        return stepBuilder.id();
    }
}
