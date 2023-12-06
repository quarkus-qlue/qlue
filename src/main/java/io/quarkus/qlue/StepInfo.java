package io.quarkus.qlue;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Information about a build step.
 */
public final class StepInfo {
    private final Consumer<StepContext> step;
    private final StepId id;
    private final Set<StepId> dependencies;
    private final Set<StepId> dependents;
    private final Set<ItemId> consumes;
    private final Set<ItemId> produces;

    StepInfo(final StepBuilder builder, Set<StepId> dependencies, Set<StepId> dependents) {
        this.id = builder.id();
        step = builder.step();
        consumes = builder.realConsumes();
        produces = builder.realProduces();
        this.dependencies = Set.copyOf(dependencies);
        this.dependents = Set.copyOf(dependents);
    }

    Consumer<StepContext> step() {
        return step;
    }

    /**
     * {@return the identifier of this step}
     */
    public StepId id() {
        return id;
    }

    /**
     * {@return the number of steps that this step depends on}
     */
    public int dependencyCount() {
        return dependencies.size();
    }

    /**
     * {@return the set of steps that this step depends on}
     */
    public Set<StepId> dependencies() {
        return dependencies;
    }

    /**
     * {@return the number of steps that depend on this step}
     */
    public int dependentCount() {
        return dependents.size();
    }

    /**
     * {@return the set of steps that depend on this step}
     */
    public Set<StepId> dependents() {
        return dependents;
    }

    /**
     * {@return the set of item identifiers that are consumed by this step}
     */
    public Set<ItemId> consumes() {
        return consumes;
    }

    /**
     * {@return the set of item identifiers that are produced by this step}
     */
    public Set<ItemId> produces() {
        return produces;
    }
}
