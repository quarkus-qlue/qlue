package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;
import static java.lang.Math.max;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

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

    private final Chain chain;
    private final ConcurrentHashMap<ItemId, Item> singles;
    private final ConcurrentHashMap<ItemId, List<Item>> multis;
    private final ConcurrentHashMap<StepInfo, StepContext> contextCache = new ConcurrentHashMap<>();
    private final Executor executor;
    private final List<Throwable> problems = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private final AtomicInteger lastStepCount = new AtomicInteger();
    private volatile Thread runningThread;
    private volatile boolean done;

    Execution(final ExecutionBuilder builder, final Executor executor) {
        chain = builder.getChain();
        this.singles = new ConcurrentHashMap<>(builder.getInitialSingle());
        this.multis = new ConcurrentHashMap<>(builder.getInitialMulti());
        this.executor = executor;
        lastStepCount.set(builder.getChain().getEndStepCount());
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
    }

    Result run() {
        final long start = System.nanoTime();
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
        if (errorReported.get()) {
            synchronized (problems) {
                return new Failure(new ArrayList<>(problems));
            }
        }
        if (lastStepCount.get() > 0) {
            throw new IllegalStateException("Extra steps left over");
        }
        return new Success(singles, multis,
                max(0, System.nanoTime() - start));
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
}
