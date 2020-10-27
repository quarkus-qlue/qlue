package io.quarkus.qlue;

import java.util.Set;
import java.util.function.Consumer;

/**
 */
final class StepInfo {
    private final Consumer<StepContext> step;
    private final int dependencies;
    private final Set<StepInfo> dependents;
    private final Set<ItemId> consumes;
    private final Set<ItemId> produces;

    StepInfo(final StepBuilder builder, int dependencies, Set<StepInfo> dependents) {
        step = builder.getStep();
        consumes = builder.getRealConsumes();
        produces = builder.getRealProduces();
        this.dependencies = dependencies;
        this.dependents = dependents;
    }

    Consumer<StepContext> getStep() {
        return step;
    }

    int getDependencies() {
        return dependencies;
    }

    Set<StepInfo> getDependents() {
        return dependents;
    }

    Set<ItemId> getConsumes() {
        return consumes;
    }

    Set<ItemId> getProduces() {
        return produces;
    }
}
