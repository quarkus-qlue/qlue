package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.MultiItem;
import io.quarkus.qlue.item.SimpleClassItem;
import io.quarkus.qlue.item.SimpleItem;
import io.smallrye.common.constraint.Assert;

/**
 * The context passed to a step's operation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StepContext {
    private final ClassLoader classLoader;
    private final StepInfo stepInfo;
    private final Execution execution;
    private final AtomicInteger dependencies;
    private volatile boolean running;

    StepContext(ClassLoader classLoader, final StepInfo stepInfo, final Execution execution) {
        this.classLoader = classLoader;
        this.stepInfo = stepInfo;
        this.execution = execution;
        dependencies = new AtomicInteger(stepInfo.getDependencies());
    }

    /**
     * Produce the given item. If the {@code type} refers to an item which is declared with multiplicity, then this
     * method can be called more than once for the given {@code type}, otherwise it must be called no more than once.
     *
     * @param item the item value (must not be {@code null})
     * @throws IllegalArgumentException if the item does not allow multiplicity but this method is called more than one time,
     *         or if the type of item could not be determined
     */
    public void produce(Item item) {
        Assert.checkNotNullParam("item", item);
        if (item instanceof ClassItem) {
            throw log.namedNeedsArgument(item.getClass());
        }
        doProduce(new ItemId(item.getClass()), item);
    }

    /**
     * Produce the given item. If the {@code type} refers to an item which is declared with multiplicity, then this
     * method can be called more than once for the given {@code type}, otherwise it must be called no more than once.
     *
     * @param <U> the upper bound of the argument type
     * @param argument the item argument (must not be {@code null})
     * @param item the item value (must not be {@code null})
     * @throws IllegalArgumentException if the item does not allow multiplicity but this method is called more than one time,
     *         or if the type of item could not be determined
     */
    public <U> void produce(Class<? extends U> argument, ClassItem<U> item) {
        Assert.checkNotNullParam("item", item);
        Assert.checkNotNullParam("argument", argument);
        doProduce(new ItemId(item.getClass(), argument), item);
    }

    /**
     * Produce the given items. This method can be called more than once for the given {@code type}
     *
     * @param items the items (must not be {@code null})
     * @throws IllegalArgumentException if the type of item could not be determined
     */
    public void produce(List<? extends MultiItem> items) {
        Assert.checkNotNullParam("items", items);
        for (MultiItem item : items) {
            doProduce(new ItemId(item.getClass()), item);
        }
    }

    /**
     * Produce the given items. This method can be called more than once for the given {@code type}. All of the
     * items produced must have the same argument.
     *
     * @param <U> the upper bound of the argument type
     * @param argument the item argument (must not be {@code null})
     * @param items the items (must not be {@code null})
     * @throws IllegalArgumentException if the type of item could not be determined
     */
    public <U> void produce(Class<? extends U> argument, List<? extends MultiClassItem<U>> items) {
        Assert.checkNotNullParam("items", items);
        Assert.checkNotNullParam("argument", argument);
        for (MultiClassItem<U> item : items) {
            doProduce(new ItemId(item.getClass(), argument), item);
        }
    }

    /**
     * Produce the given item. If the {@code type} refers to an item which is declared with multiplicity, then this
     * method can be called more than once for the given {@code type}, otherwise it must be called no more than once.
     *
     * @param type the item type (must not be {@code null})
     * @param item the item value (may be {@code null})
     * @param <T> the item type
     * @throws IllegalArgumentException if this step was not declared to produce {@code type}, or if {@code type} is
     *         {@code null}, or if the item does not allow multiplicity but this method is called more than one time
     */
    public <T extends Item> void produce(Class<T> type, T item) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        doProduce(new ItemId(type), type.cast(item));
    }

    /**
     * Produce the given item. If the {@code type} refers to an item which is declared with multiplicity, then this
     * method can be called more than once for the given {@code type}, otherwise it must be called no more than once.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param item the item value (may be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @throws IllegalArgumentException if this step was not declared to produce {@code type}, or if {@code type} is
     *         {@code null}, or if the item does not allow multiplicity but this method is called more than one time
     */
    public <U, T extends ClassItem<U>> void produce(Class<T> type, Class<? extends U> argument, T item) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        doProduce(new ItemId(type, argument), type.cast(item));
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     * @throws ClassCastException if the cast failed
     */
    public <T extends SimpleItem> T consume(Class<T> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        if (!running) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type);
        if (id.isMulti()) {
            throw log.cannotMulti(id);
        }
        if (!stepInfo.getConsumes().contains(id)) {
            throw log.undeclaredItem(id);
        }
        return type.cast(execution.getSingles().get(id));
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     * @throws ClassCastException if the cast failed
     */
    public <U, T extends SimpleClassItem<U>> T consume(Class<T> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        if (!running) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type, argument);
        if (id.isMulti()) {
            throw log.cannotMulti(id);
        }
        if (!stepInfo.getConsumes().contains(id)) {
            throw log.undeclaredItem(id);
        }
        return type.cast(execution.getSingles().get(id));
    }

    /**
     * Consume all of the values produced for the named item. If the
     * item type implements {@link Comparable}, it will be sorted by natural order before return. The returned list
     * is a mutable copy.
     *
     * @param type the item element type (must not be {@code null})
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T extends MultiItem> List<T> consumeMulti(Class<T> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        if (!running) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type);
        if (!id.isMulti()) {
            // can happen if obj changes base class
            throw log.cannotMulti(id);
        }
        if (!stepInfo.getConsumes().contains(id)) {
            throw log.undeclaredItem(id);
        }
        return new ArrayList<>((List<T>) (List) execution.getMultis().getOrDefault(id, Collections.emptyList()));
    }

    /**
     * Consume all of the values produced for the named item. If the
     * item type implements {@link Comparable}, it will be sorted by natural order before return. The returned list
     * is a mutable copy.
     *
     * @param type the item element type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <U, T extends MultiClassItem<U>> List<T> consumeMulti(Class<T> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        if (!running) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type, argument);
        if (!id.isMulti()) {
            // can happen if obj changes base class
            throw log.cannotMulti(id);
        }
        if (!stepInfo.getConsumes().contains(id)) {
            throw log.undeclaredItem(id);
        }
        return new ArrayList<>((List<T>) (List) execution.getMultis().getOrDefault(id, Collections.emptyList()));
    }

    /**
     * Consume all of the values produced for the named item, re-sorting it according
     * to the given comparator. The returned list is a mutable copy.
     *
     * @param type the item element type (must not be {@code null})
     * @param comparator the comparator to use (must not be {@code null})
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     */
    public <T extends MultiItem> List<T> consumeMulti(Class<T> type, Comparator<? super T> comparator) {
        final List<T> result = consumeMulti(type);
        result.sort(comparator);
        return result;
    }

    /**
     * Consume all of the values produced for the named item, re-sorting it according
     * to the given comparator. The returned list is a mutable copy.
     *
     * @param type the item element type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param comparator the comparator to use (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     */
    public <U, T extends MultiClassItem<U>> List<T> consumeMulti(Class<T> type, Class<? extends U> argument,
            Comparator<? super T> comparator) {
        final List<T> result = consumeMulti(type, argument);
        result.sort(comparator);
        return result;
    }

    /**
     * Determine if an item was produced and is therefore available to be {@linkplain #consume(Class) consumed}.
     *
     * @param type the item type (must not be {@code null})
     * @return {@code true} if the item was produced and is available, {@code false} if it was not or if this step does
     *         not consume the named item
     */
    public boolean isAvailableToConsume(Class<? extends Item> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        final ItemId id = new ItemId(type);
        return stepInfo.getConsumes().contains(id) && id.isMulti()
                ? !execution.getMultis().getOrDefault(id, Collections.emptyList()).isEmpty()
                : execution.getSingles().containsKey(id);
    }

    /**
     * Determine if an item was produced and is therefore available to be {@linkplain #consume(Class,Class) consumed}.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return {@code true} if the item was produced and is available, {@code false} if it was not or if this step does
     *         not consume the named item
     */
    public <U> boolean isAvailableToConsume(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        final ItemId id = new ItemId(type, argument);
        return stepInfo.getConsumes().contains(id) && id.isMulti()
                ? !execution.getMultis().getOrDefault(id, Collections.emptyList()).isEmpty()
                : execution.getSingles().containsKey(id);
    }

    /**
     * Determine if an item will be consumed in this execution. If an item is not consumed, then steps are not
     * required to produce it.
     *
     * @param type the item type (must not be {@code null})
     * @return {@code true} if the item will be consumed, {@code false} if it will not be or if this step does
     *         not produce the named item
     */
    public boolean isConsumed(Class<? extends Item> type) {
        Assert.checkNotNullParam("type", type);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        return execution.getBuildChain().getConsumed().contains(new ItemId(type));
    }

    /**
     * Determine if an item will be consumed in this execution. If an item is not consumed, then steps are not
     * required to produce it.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @return {@code true} if the item will be consumed, {@code false} if it will not be or if this step does
     *         not produce the named item
     */
    public <U> boolean isConsumed(Class<? extends ClassItem<U>> type, Class<? extends U> argument) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("argument", argument);
        if (ClassItem.class.isAssignableFrom(type)) {
            throw log.namedNeedsArgument(type);
        }
        return execution.getBuildChain().getConsumed().contains(new ItemId(type, argument));
    }

    /**
     * Mark the execution as failed and halt the process as soon as possible.
     */
    public void markAsFailed() {
        execution.setErrorReported();
    }

    /**
     * Report a problem and mark the execution as failed.
     *
     * @param problem the problem to report
     */
    public void addProblem(final Throwable problem) {
        execution.getProblems().add(problem);
        markAsFailed();
    }

    /**
     * Get an executor which can be used for asynchronous tasks.
     *
     * @return an executor which can be used for asynchronous tasks
     */
    public Executor getExecutor() {
        return execution.getExecutor();
    }

    // -- //

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void doProduce(ItemId id, Item value) {
        if (!running) {
            throw log.stepNotRunning();
        }
        if (!stepInfo.getProduces().contains(id)) {
            throw log.undeclaredItem(id);
        }
        if (id.isMulti()) {
            final List<Item> list = execution.getMultis().computeIfAbsent(id, x -> new ArrayList<>());
            synchronized (list) {
                if (Comparable.class.isAssignableFrom(id.getType())) {
                    int pos = Collections.binarySearch((List) list, value);
                    if (pos < 0)
                        pos = -(pos + 1);
                    list.add(pos, value);
                } else {
                    list.add(value);
                }
            }
        } else {
            if (execution.getSingles().putIfAbsent(id, value) != null) {
                throw log.cannotMulti(id);
            }
        }
    }

    void depFinished() {
        final int remaining = dependencies.decrementAndGet();
        log.tracef("Dependency of \"%2$s\" finished; %1$d remaining", remaining, stepInfo.getStep());
        if (remaining == 0) {
            execution.getExecutor().execute(this::run);
        }
    }

    void run() {
        final Execution execution = this.execution;
        final StepInfo stepInfo = this.stepInfo;
        final Consumer<StepContext> step = stepInfo.getStep();
        final long start = System.nanoTime();
        log.startingStep(step);
        try {
            if (!execution.isErrorReported()) {
                running = true;
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    step.accept(this);
                } catch (Throwable t) {
                    log.stepFailed(t, step);
                    execution.getProblems().add(t);
                    execution.setErrorReported();
                } finally {
                    running = false;
                    Thread.currentThread().setContextClassLoader(old);
                }
            }
        } finally {
            if (log.isTraceEnabled()) {
                log.finishingStep(step, Duration.of(System.nanoTime() - start, ChronoUnit.NANOS));
            }
            execution.removeStepContext(stepInfo, this);
        }
        final Set<StepInfo> dependents = stepInfo.getDependents();
        if (!dependents.isEmpty()) {
            for (StepInfo info : dependents) {
                execution.getStepContext(info).depFinished();
            }
        } else {
            execution.depFinished();
        }
    }
}
