package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;
import static java.lang.invoke.MethodHandles.lookup;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.time.Instant;
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
    private static final VarHandle stateHandle = ConstantBootstraps.fieldVarHandle(lookup(), "state", VarHandle.class,
            StepContext.class, State.class);

    private final ClassLoader classLoader;
    private final StepInfo stepInfo;
    private final Execution execution;
    private final AtomicInteger dependencies;
    @SuppressWarnings({ "unused", "FieldMayBeFinal" }) // stateHandle
    private volatile State state = State.WAITING;
    private volatile Instant start;
    private volatile Instant end;
    private volatile Duration duration;
    private AttachmentKey<?> key1;
    private Object val1;
    private AttachmentKey<?> key2;
    private Object val2;

    StepContext(ClassLoader classLoader, final StepInfo stepInfo, final Execution execution) {
        this.classLoader = classLoader;
        this.stepInfo = stepInfo;
        this.execution = execution;
        dependencies = new AtomicInteger(stepInfo.dependencyCount());
    }

    /**
     * {@return the current state of this context}
     */
    public State state() {
        return state;
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
        if (state != State.RUNNING) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type);
        if (id.isMulti()) {
            throw log.cannotMulti(id);
        }
        if (!stepInfo.consumes().contains(id)) {
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
        if (state != State.RUNNING) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type, argument);
        if (id.isMulti()) {
            throw log.cannotMulti(id);
        }
        if (!stepInfo.consumes().contains(id)) {
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
        if (state != State.RUNNING) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type);
        if (!id.isMulti()) {
            // can happen if obj changes base class
            throw log.cannotMulti(id);
        }
        if (!stepInfo.consumes().contains(id)) {
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
        if (state != State.RUNNING) {
            throw log.stepNotRunning();
        }
        final ItemId id = new ItemId(type, argument);
        if (!id.isMulti()) {
            // can happen if obj changes base class
            throw log.cannotMulti(id);
        }
        if (!stepInfo.consumes().contains(id)) {
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
        return stepInfo.consumes().contains(id) && id.isMulti()
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
        return stepInfo.consumes().contains(id) && id.isMulti()
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
     * {@return the start instant of this step}
     *
     * @throws IllegalStateException if the step has not yet started
     */
    public Instant start() {
        Instant start = this.start;
        if (start == null) {
            throw log.stepNotStarted(stepInfo.id());
        }
        return start;
    }

    /**
     * {@return the end instant of this step}
     *
     * @throws IllegalStateException if the step has not yet completed
     */
    public Instant end() {
        Instant end = this.end;
        if (end == null) {
            throw log.stepNotEnded(stepInfo.id());
        }
        return end;
    }

    /**
     * Get the duration of this step.
     *
     * @return the duration (not {@code null})
     * @throws IllegalStateException if the step has not yet completed
     */
    public Duration duration() {
        Duration duration = this.duration;
        if (duration == null) {
            duration = this.duration = max(Duration.between(start(), end()), Duration.ZERO);
        }
        return duration;
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) > 0 ? a : b;
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

    /**
     * Get the attachment for the given key, if any.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the attachment value or {@code null} if none is present
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttachment(AttachmentKey<T> key) {
        if (key == key1) {
            return (T) val1;
        } else if (key == key2) {
            return (T) val2;
        } else {
            return null;
        }
    }

    /**
     * Determine whether the given key is attached.
     *
     * @param key the key
     * @return {@code true} if the key is attached, {@code false} otherwise
     */
    public boolean hasAttachment(AttachmentKey<?> key) {
        return key == key1 || key == key2;
    }

    /**
     * Put an attachment on to this object.
     *
     * @param key the attachment key (must not be {@code null})
     * @param value the attachment value (must not be {@code null})
     * @param <T> the value type
     * @return the previous value of the attachment, or {@code null} if there was no previous value
     */
    @SuppressWarnings("unchecked")
    public <T> T putAttachment(AttachmentKey<T> key, T value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);
        if (key == key1) {
            T old = (T) val1;
            val1 = value;
            return old;
        } else if (key == key2) {
            T old = (T) val2;
            val2 = value;
            return old;
        } else if (key1 == null) {
            key1 = key;
            val1 = value;
            return null;
        } else if (key2 == null) {
            key2 = key;
            val2 = value;
            return null;
        } else {
            throw log.tooManyAttachments();
        }
    }

    /**
     * Put an attachment on to this object if it is not already present.
     *
     * @param key the attachment key (must not be {@code null})
     * @param value the attachment value (must not be {@code null})
     * @param <T> the value type
     * @return the previous value of the attachment, or {@code null} if there was no previous value
     */
    @SuppressWarnings("unchecked")
    public <T> T putAttachmentIfAbsent(AttachmentKey<T> key, T value) {
        Assert.checkNotNullParam("key", key);
        Assert.checkNotNullParam("value", value);
        if (key == key1) {
            return (T) val1;
        } else if (key == key2) {
            return (T) val2;
        } else if (key1 == null) {
            key1 = key;
            val1 = value;
            return null;
        } else if (key2 == null) {
            key2 = key;
            val2 = value;
            return null;
        } else {
            throw log.tooManyAttachments();
        }
    }

    // -- //

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void doProduce(ItemId id, Item value) {
        if (state != State.RUNNING) {
            throw log.stepNotRunning();
        }
        if (!stepInfo.produces().contains(id)) {
            throw log.undeclaredItem(id);
        }
        if (id.isMulti()) {
            final List<Item> list = execution.getMultis().computeIfAbsent(id, x -> new ArrayList<>());
            synchronized (list) {
                if (Comparable.class.isAssignableFrom(id.itemType())) {
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
        log.tracef("Dependency of \"%2$s\" finished; %1$d remaining", remaining, stepInfo.step());
        if (remaining == 0) {
            execution.getExecutor().execute(this::run);
        }
    }

    void run() {
        final Execution execution = this.execution;
        final StepInfo stepInfo = this.stepInfo;
        final Consumer<StepContext> step = stepInfo.step();
        try {
            if (execution.isErrorReported()) {
                this.start = this.end = execution.clock().instant();
                casStateRequired(State.WAITING, State.SKIPPED);
                log.skippedStep(step);
            } else {
                log.startingStep(step);
                casStateRequired(State.WAITING, State.RUNNING);
                this.start = execution.clock().instant();
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(classLoader);
                try {
                    step.accept(this);
                    casStateRequired(State.RUNNING, State.COMPLETE);
                    this.end = execution.clock().instant();
                } catch (Throwable t) {
                    casStateRequired(State.RUNNING, State.FAILED);
                    this.end = execution.clock().instant();
                    log.stepFailed(t, step);
                    execution.getProblems().add(t);
                    execution.setErrorReported();
                } finally {
                    Thread.currentThread().setContextClassLoader(old);
                    if (log.isTraceEnabled()) {
                        log.finishingStep(step, duration());
                    }
                }
            }
        } finally {
            execution.removeStepContext(stepInfo, this);
        }
        final Set<StepId> dependents = stepInfo.dependents();
        if (!dependents.isEmpty()) {
            for (StepId id : dependents) {
                execution.getStepContext(execution.chain().stepInfo(id)).depFinished();
            }
        } else {
            execution.depFinished();
        }
    }

    private void casStateRequired(State expect, State update) {
        if (!stateHandle.compareAndSet(this, expect, update)) {
            throw new IllegalStateException("Unexpected state: " + expect);
        }
    }

    StepSummary summary() {
        return new StepSummary(stepInfo.id(), state, start, end);
    }

    /**
     * The current state of execution for this context.
     */
    public enum State {
        /**
         * The context is awaiting execution.
         */
        WAITING(false),
        /**
         * The step is currently executing.
         * During this state, values may be produced and consumed.
         */
        RUNNING(false),
        /**
         * The step has completed.
         */
        COMPLETE(true),
        /**
         * The step has failed.
         */
        FAILED(true),
        /**
         * The step has been skipped due to a prior step's failure.
         */
        SKIPPED(true),
        ;

        private final boolean finished;

        State(final boolean finished) {
            this.finished = finished;
        }

        /**
         * {@return <code>true</code> if this state is a terminal state, or <code>false</code> if it is not}
         */
        public boolean finished() {
            return finished;
        }
    }
}
