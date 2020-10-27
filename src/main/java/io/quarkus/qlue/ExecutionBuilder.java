package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.Item;
import io.smallrye.common.constraint.Assert;

/**
 * A builder for an execution.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ExecutionBuilder {
    private final Chain chain;
    private final Map<ItemId, Item> initialSingle;
    private final Map<ItemId, ArrayList<Item>> initialMulti;

    ExecutionBuilder(final Chain chain) {
        this.chain = chain;
        initialSingle = new HashMap<>(chain.getInitialSingleCount());
        initialMulti = new HashMap<>(chain.getInitialMultiCount());
    }

    /**
     * Provide an initial item.
     *
     * @param item the item value
     * @return this builder
     * @throws IllegalArgumentException if this chain was not declared to initially produce {@code type},
     *         or if the item does not allow multiplicity but this method is called more than one time
     */
    public <T extends Item> ExecutionBuilder produce(T item) {
        Assert.checkNotNullParam("item", item);
        if (item instanceof ClassItem) {
            throw log.namedNeedsArgument(item.getClass());
        }
        produce(new ItemId(item.getClass()), item);
        return this;
    }

    /**
     * Provide an initial item.
     *
     * @param type the item type (must not be {@code null})
     * @param item the item value
     * @return this builder
     * @throws IllegalArgumentException if this chain was not declared to initially produce {@code type},
     *         or if {@code type} is {@code null}, or if the item does not allow multiplicity but this method is called
     *         more than one time
     */
    public <T extends Item> ExecutionBuilder produce(Class<T> type, T item) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("item", item);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(item.getClass());
        }
        produce(new ItemId(type), item);
        return this;
    }

    /**
     * Provide an initial item.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param item the item value
     * @param <U> the upper bound of the argument type
     * @return this builder
     * @throws IllegalArgumentException if this chain was not declared to initially produce {@code type},
     *         or if {@code type} is {@code null}, or if the item does not allow multiplicity but this method is called
     *         more than one time
     */
    public <U, T extends ClassItem<U>> ExecutionBuilder produce(Class<T> type, Class<? extends U> argument, T item) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("item", item);
        if (!ClassItem.class.isAssignableFrom(type)) {
            throw log.unnamedMustNotHaveArgument(item.getClass());
        }
        produce(new ItemId(type, argument), item);
        return this;
    }

    /**
     * Run the execution. The chain may run in one or many threads.
     *
     * @param executor the executor to use for this execution (must not be {@code null})
     * @return the execution result (not {@code null})
     */
    public Result execute(Executor executor) {
        return new Execution(this, Assert.checkNotNullParam("executor", executor)).run();
    }

    // -- //

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void produce(final ItemId id, final Item value) {
        if (!chain.hasInitial(id)) {
            throw log.undeclaredItem(id);
        }
        if (id.isMulti()) {
            final List<Item> list = initialMulti.computeIfAbsent(id, x -> new ArrayList<>());
            if (Comparable.class.isAssignableFrom(id.getType())) {
                int pos = Collections.binarySearch((List) list, value);
                if (pos < 0)
                    pos = -(pos + 1);
                list.add(pos, value);
            } else {
                list.add(value);
            }
        } else {
            if (initialSingle.putIfAbsent(id, value) != null) {
                throw log.cannotMulti(id);
            }
        }
    }

    Map<ItemId, Item> getInitialSingle() {
        return initialSingle;
    }

    Map<ItemId, ArrayList<Item>> getInitialMulti() {
        return initialMulti;
    }

    Chain getChain() {
        return chain;
    }
}
