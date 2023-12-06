package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkus.qlue.item.Item;

/**
 */
final class Execution {

    private final Clock clock;
    private final Chain chain;
    private final ConcurrentHashMap<ItemId, Item> singles;
    private final ConcurrentHashMap<ItemId, List<Item>> multis;
    private final ConcurrentHashMap<StepInfo, StepContext> contextCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StepId, StepSummary> summaries = new ConcurrentHashMap<>();
    private final Executor executor;
    private final List<Throwable> problems = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private final AtomicInteger lastStepCount = new AtomicInteger();
    private volatile Thread runningThread;
    private volatile boolean done;

    Execution(final ExecutionBuilder builder, final Executor executor) {
        chain = builder.chain();
        clock = builder.clock();
        this.singles = new ConcurrentHashMap<>(builder.initialSingle());
        this.multis = new ConcurrentHashMap<>(builder.initialMulti());
        this.executor = executor;
        lastStepCount.set(builder.chain().getEndStepCount());
        if (lastStepCount.get() == 0)
            done = true;
    }

    List<Throwable> getProblems() {
        return problems;
    }

    StepContext getStepContext(StepInfo stepInfo) {
        return contextCache.computeIfAbsent(stepInfo, si -> new StepContext(chain.getClassLoader(), si, this));
    }

    void removeStepContext(StepInfo stepInfo, StepContext stepContext) {
        contextCache.remove(stepInfo, stepContext);
        summaries.put(stepInfo.id(), stepContext.summary());
    }

    Chain chain() {
        return chain;
    }

    Result run() {
        Clock clock = this.clock;
        final Instant start = clock.instant();
        runningThread = Thread.currentThread();
        // run the operation
        final List<StepInfo> startSteps = chain.getStartSteps();
        for (StepInfo startStep : startSteps) {
            executor.execute(getStepContext(startStep)::run);
        }
        // wait for the wrap-up
        boolean intr = false;
        try {
            for (;;) {
                if (Thread.interrupted()) {
                    intr = true;
                }
                if (done) {
                    break;
                }
                park(this);
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
            runningThread = null;
        }
        final Instant end = clock.instant();
        if (errorReported.get()) {
            synchronized (problems) {
                return new Failure(start, end, new ArrayList<>(problems), summaries);
            }
        }
        if (lastStepCount.get() > 0) {
            throw new IllegalStateException("Extra steps left over");
        }
        return new Success(start, end, singles, multis, summaries);
    }

    Executor getExecutor() {
        return executor;
    }

    void setErrorReported() {
        errorReported.compareAndSet(false, true);
    }

    boolean isErrorReported() {
        return errorReported.get();
    }

    ConcurrentHashMap<ItemId, Item> getSingles() {
        return singles;
    }

    ConcurrentHashMap<ItemId, List<Item>> getMultis() {
        return multis;
    }

    Chain getBuildChain() {
        return chain;
    }

    void depFinished() {
        final int count = lastStepCount.decrementAndGet();
        log.stepCompleted(count);
        if (count == 0) {
            done = true;
            unpark(runningThread);
        }
    }

    Clock clock() {
        return clock;
    }
}
