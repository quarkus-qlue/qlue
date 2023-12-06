package io.quarkus.qlue;

/**
 *
 */
record Produce(StepBuilder stepBuilder, ItemId itemId, Constraint constraint, ProduceFlags flags) {
    Produce {
    }

    Produce combine(final Constraint constraint, final ProduceFlags flags) {
        final Constraint outputConstraint;
        ProduceFlags outputFlags;
        if (constraint == Constraint.REAL || this.constraint == Constraint.REAL) {
            outputConstraint = Constraint.REAL;
        } else {
            outputConstraint = Constraint.ORDER_ONLY;
        }
        outputFlags = flags.with(this.flags);
        if (!flags.contains(ProduceFlag.WEAK) || !this.flags.contains(ProduceFlag.WEAK)) {
            outputFlags = outputFlags.without(ProduceFlag.WEAK);
        }
        if (!flags.contains(ProduceFlag.OVERRIDABLE) || !this.flags.contains(ProduceFlag.OVERRIDABLE)) {
            outputFlags = outputFlags.without(ProduceFlag.OVERRIDABLE);
        }
        return new Produce(stepBuilder, itemId, outputConstraint, outputFlags);
    }

    boolean isOverridable() {
        return flags.contains(ProduceFlag.OVERRIDABLE);
    }

    boolean isReal() {
        return constraint == Constraint.REAL;
    }

    StepId stepId() {
        return stepBuilder.id();
    }
}
