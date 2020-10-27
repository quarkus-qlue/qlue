package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.EmptyItem;
import io.quarkus.qlue.item.Item;
import io.smallrye.common.constraint.Assert;

/**
 * A builder for step instances within a chain. A step can consume and produce items. It may also register
 * a destructor for items it produces, which will be run (in indeterminate order) at the end of processing.
 */
public final class StepBuilder {
    private final ChainBuilder chainBuilder;
    private final Consumer<StepContext> step;
    private final Map<ItemId, Consume> consumes = new HashMap<>();
    private final Map<ItemId, Produce> produces = new HashMap<>();

    StepBuilder(final ChainBuilder chainBuilder, final Consumer<StepContext> step) {
        this.chainBuilder = chainBuilder;
        this.step = step;
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     */
    public StepBuilder beforeConsume(Class<? extends Item> type) {
        return beforeConsume(type, ProduceFlags.NONE);
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param flag the producer flag to apply (must not be {@code null})
     * @return this builder
     */
    public StepBuilder beforeConsume(Class<? extends Item> type, ProduceFlag flag) {
        return beforeConsume(type, ProduceFlags.of(Assert.checkNotNullParam("flag", flag)));
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param flags the producer flags to apply (must not be {@code null})
     * @return this builder
     */
    public StepBuilder beforeConsume(Class<? extends Item> type, ProduceFlags flags) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        Assert.checkNotNullParam("flags", flags);
        addProduces(new ItemId(type), Constraint.ORDER_ONLY, flags);
        return this;
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder beforeConsume(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        return beforeConsume(type, argument, ProduceFlags.NONE);
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flag the producer flag to apply (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder beforeConsume(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ProduceFlag flag) {
        return beforeConsume(type, argument, ProduceFlags.of(Assert.checkNotNullParam("flag", flag)));
    }

    /**
     * This step should complete before any steps which consume the given item {@code type} are initiated.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flags the producer flags to apply (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder beforeConsume(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ProduceFlags flags) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        Assert.checkNotNullParam("flags", flags);
        addProduces(new ItemId(type, argument), Constraint.ORDER_ONLY, flags);
        return this;
    }

    /**
     * This step should be initiated after any steps which produce the given item {@code type} are completed.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     */
    public StepBuilder afterProduce(Class<? extends Item> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        addConsumes(new ItemId(type), Constraint.ORDER_ONLY, ConsumeFlags.of(ConsumeFlag.OPTIONAL));
        return this;
    }

    /**
     * This step should be initiated after any steps which produce the given item {@code type} are completed.
     * If no such steps exist, no ordering constraint is enacted.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder afterProduce(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        addConsumes(new ItemId(type, argument), Constraint.ORDER_ONLY, ConsumeFlags.of(ConsumeFlag.OPTIONAL));
        return this;
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     */
    public StepBuilder produces(Class<? extends Item> type) {
        return produces(type, ProduceFlags.NONE);
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param flag the producer flag to apply (must not be {@code null})
     * @return this builder
     */
    public StepBuilder produces(Class<? extends Item> type, ProduceFlag flag) {
        return produces(type, ProduceFlags.of(Assert.checkNotNullParam("flag", flag)));
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param flag1 the first producer flag to apply (must not be {@code null})
     * @param flag2 the second producer flag to apply (must not be {@code null})
     * @return this builder
     */
    public StepBuilder produces(Class<? extends Item> type, ProduceFlag flag1, ProduceFlag flag2) {
        return produces(type,
                ProduceFlags.of(Assert.checkNotNullParam("flag1", flag1)).with(Assert.checkNotNullParam("flag2", flag2)));
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param flags the producer flag to apply (must not be {@code null})
     * @return this builder
     */
    public StepBuilder produces(Class<? extends Item> type, ProduceFlags flags) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("flag", flags);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        if (EmptyItem.class.isAssignableFrom(type)) {
            throw log.emptyItemProduced();
        }
        addProduces(new ItemId(type), Constraint.REAL, flags);
        return this;
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder produces(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        if (EmptyItem.class.isAssignableFrom(type)) {
            throw log.emptyItemProduced();
        }
        addProduces(new ItemId(type, argument), Constraint.REAL, ProduceFlags.NONE);
        return this;
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flag the producer flag to apply (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder produces(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ProduceFlag flag) {
        return produces(type, argument, ProduceFlags.of(flag));
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flag1 the first producer flag to apply (must not be {@code null})
     * @param flag2 the second producer flag to apply (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder produces(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ProduceFlag flag1,
            ProduceFlag flag2) {
        return produces(type, argument, ProduceFlags.of(flag1).with(flag2));
    }

    /**
     * Similarly to {@link #beforeConsume(Class)}, establish that this step must come before the consumer(s) of the
     * given item {@code type}; however, only one {@code producer} may exist for the given item. In addition, the
     * step may produce an actual value for this item, which will be shared to all consumers during execution.
     *
     * @param type the item type (must not be {@code null})
     * @param flags the producer flag to apply (must not be {@code null})
     * @return this builder
     */
    public <U> StepBuilder produces(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ProduceFlags flags) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("flags", flags);
        if (EmptyItem.class.isAssignableFrom(type)) {
            throw log.emptyItemProduced();
        }
        addProduces(new ItemId(type, argument), Constraint.REAL, flags);
        return this;
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @return this builder
     */
    public StepBuilder consumes(Class<? extends Item> type) {
        return consumes(type, ConsumeFlags.NONE);
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @param flag a flag which modifies the consume operation (must not be {@code null})
     * @return this builder
     */
    public StepBuilder consumes(Class<? extends Item> type, ConsumeFlag flag) {
        return consumes(type, ConsumeFlags.of(Assert.checkNotNullParam("flag", flag)));
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @param flags a set of flags which modify the consume operation (must not be {@code null})
     * @return this builder
     */
    public StepBuilder consumes(Class<? extends Item> type, ConsumeFlags flags) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("flags", flags);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        if (EmptyItem.class.isAssignableFrom(type)) {
            throw log.emptyItemConsumed();
        }
        addConsumes(new ItemId(type), Constraint.REAL, flags);
        return this;
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder consumes(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        return consumes(type, argument, ConsumeFlags.NONE);
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flag a flag which modifies the consume operation (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder consumes(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ConsumeFlag flag) {
        return consumes(type, argument, ConsumeFlags.of(Assert.checkNotNullParam("flag", flag)));
    }

    /**
     * This step consumes the given produced item. The item must be produced somewhere in the chain. If
     * no such producer exists, the chain will not be constructed; instead, an error will be raised.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param flags a set of flags which modify the consume operation (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return this builder
     */
    public <U> StepBuilder consumes(Class<? extends ClassItem<U>> type, Class<? extends U> argument, ConsumeFlags flags) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        Assert.checkNotNullParam("flags", flags);
        if (EmptyItem.class.isAssignableFrom(type)) {
            throw log.emptyItemConsumed();
        }
        addConsumes(new ItemId(type, argument), Constraint.REAL, flags);
        return this;
    }

    /**
     * Get the chain builder.
     *
     * @return the chain builder (not {@code null})
     */
    public ChainBuilder getChainBuilder() {
        return chainBuilder;
    }

    /**
     * Build this step into the chain.
     *
     * @return the chain builder that this step was added to
     */
    public ChainBuilder build() {
        final ChainBuilder chainBuilder = this.chainBuilder;
        chainBuilder.addStep(this);
        return chainBuilder;
    }

    /**
     * Build this step into the chain if the supplier returns {@code true}.
     *
     * @param supp the {@code boolean} supplier (must not be {@code null})
     * @return the chain builder that this step was added to, or {@code null} if it was not added
     */
    public ChainBuilder buildIf(BooleanSupplier supp) {
        return supp.getAsBoolean() ? build() : null;
    }

    // -- //

    Consumer<StepContext> getStep() {
        return step;
    }

    private void addConsumes(final ItemId itemId, final Constraint constraint, final ConsumeFlags flags) {
        Assert.checkNotNullParam("flags", flags);
        consumes.compute(itemId,
                (id, c) -> c == null ? new Consume(this, itemId, constraint, flags) : c.combine(constraint, flags));
    }

    private void addProduces(final ItemId itemId, final Constraint constraint, final ProduceFlags flags) {
        produces.compute(itemId,
                (id, p) -> p == null ? new Produce(this, itemId, constraint, flags) : p.combine(constraint, flags));
    }

    Map<ItemId, Consume> getConsumes() {
        return consumes;
    }

    Map<ItemId, Produce> getProduces() {
        return produces;
    }

    Set<ItemId> getRealConsumes() {
        final HashMap<ItemId, Consume> map = new HashMap<>(consumes);
        map.entrySet().removeIf(e -> e.getValue().getConstraint() == Constraint.ORDER_ONLY);
        return map.keySet();
    }

    Set<ItemId> getRealProduces() {
        final HashMap<ItemId, Produce> map = new HashMap<>(produces);
        map.entrySet().removeIf(e -> e.getValue().getConstraint() == Constraint.ORDER_ONLY);
        return map.keySet();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Step [");
        builder.append(step);
        builder.append("]");
        return builder.toString();
    }
}
