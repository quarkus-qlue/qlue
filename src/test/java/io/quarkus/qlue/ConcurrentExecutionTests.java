package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.item.SimpleItem;

/**
 * Concurrent stress tests for verifying Qlue's state transitions, topological execution sequence,
 * and lock-free thread safety under multi-threaded parallel execution.
 */
public class ConcurrentExecutionTests {

    /**
     * Default constructor for ConcurrentExecutionTests.
     */
    public ConcurrentExecutionTests() {
    }

    /**
     * Base test simple item.
     */
    public abstract static class BaseItem extends SimpleItem {
        /**
         * Default constructor.
         */
        protected BaseItem() {
        }
    }

    /** Simple item subclass. */
    public static final class Item1 extends BaseItem {
        /** Default constructor. */
        public Item1() {
        }
    }

    /** Simple item subclass. */
    public static final class Item2 extends BaseItem {
        /** Default constructor. */
        public Item2() {
        }
    }

    /** Simple item subclass. */
    public static final class Item3 extends BaseItem {
        /** Default constructor. */
        public Item3() {
        }
    }

    /** Simple item subclass. */
    public static final class Item4 extends BaseItem {
        /** Default constructor. */
        public Item4() {
        }
    }

    /** Simple item subclass. */
    public static final class Item5 extends BaseItem {
        /** Default constructor. */
        public Item5() {
        }
    }

    /** Simple item subclass. */
    public static final class Item6 extends BaseItem {
        /** Default constructor. */
        public Item6() {
        }
    }

    /** Simple item subclass. */
    public static final class Item7 extends BaseItem {
        /** Default constructor. */
        public Item7() {
        }
    }

    /**
     * Verifies that executing a complex, branching dependency DAG on a multi-threaded executor service
     * executes all steps in strict topological order and terminates successfully without race conditions or deadlocks.
     *
     * @throws Exception if execution or thread coordination fails
     */
    @Test
    public void testParallelTopologicalExecution() throws Exception {
        final ChainBuilder builder = Chain.builder();
        final List<String> eventLog = Collections.synchronizedList(new ArrayList<>());

        // S1 produces Item1. Independent.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S1-START");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
                context.produce(new Item1());
                eventLog.add("S1-END");
            }
        }).produces(Item1.class).build();

        // S2 produces Item2. Independent.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S2-START");
                try {
                    Thread.sleep(15);
                } catch (InterruptedException ignored) {
                }
                context.produce(new Item2());
                eventLog.add("S2-END");
            }
        }).produces(Item2.class).build();

        // S3 consumes Item1 and Item2, produces Item3.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S3-START");
                context.consume(Item1.class);
                context.consume(Item2.class);
                context.produce(new Item3());
                eventLog.add("S3-END");
            }
        }).consumes(Item1.class).consumes(Item2.class).produces(Item3.class).build();

        // S4 produces Item4. Independent.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S4-START");
                context.produce(new Item4());
                eventLog.add("S4-END");
            }
        }).produces(Item4.class).build();

        // S5 consumes Item3 and Item4, produces Item5.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S5-START");
                context.consume(Item3.class);
                context.consume(Item4.class);
                context.produce(new Item5());
                eventLog.add("S5-END");
            }
        }).consumes(Item3.class).consumes(Item4.class).produces(Item5.class).build();

        // S6 produces Item6. Independent.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S6-START");
                context.produce(new Item6());
                eventLog.add("S6-END");
            }
        }).produces(Item6.class).build();

        // S7 consumes Item5 and Item6, produces Item7.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                eventLog.add("S7-START");
                context.consume(Item5.class);
                context.consume(Item6.class);
                context.produce(new Item7());
                eventLog.add("S7-END");
            }
        }).consumes(Item5.class).consumes(Item6.class).produces(Item7.class).build();

        builder.addFinal(Item7.class);
        final Chain chain = builder.build();

        final ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            final Result result = chain.createExecutionBuilder().execute(executor);
            assertTrue(result.isSuccess());
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        // Verify topological order based on logged events.
        final Map<String, Integer> startIndices = new HashMap<>();
        final Map<String, Integer> endIndices = new HashMap<>();

        for (int i = 0; i < eventLog.size(); i++) {
            final String event = eventLog.get(i);
            if (event.endsWith("-START")) {
                startIndices.put(event.substring(0, 2), i);
            } else if (event.endsWith("-END")) {
                endIndices.put(event.substring(0, 2), i);
            }
        }

        // S3 depends on S1 and S2
        assertPrecedence("S1", "S3", startIndices, endIndices);
        assertPrecedence("S2", "S3", startIndices, endIndices);

        // S5 depends on S3 and S4
        assertPrecedence("S3", "S5", startIndices, endIndices);
        assertPrecedence("S4", "S5", startIndices, endIndices);

        // S7 depends on S5 and S6
        assertPrecedence("S5", "S7", startIndices, endIndices);
        assertPrecedence("S6", "S7", startIndices, endIndices);
    }

    private void assertPrecedence(final String predecessor, final String successor,
            final Map<String, Integer> startIndices, final Map<String, Integer> endIndices) {
        final Integer preEnd = endIndices.get(predecessor);
        final Integer succStart = startIndices.get(successor);

        assertTrue(preEnd != null, predecessor + " did not finish");
        assertTrue(succStart != null, successor + " did not start");
        assertTrue(preEnd < succStart, predecessor + " did not finish before " + successor + " started");
    }
}
