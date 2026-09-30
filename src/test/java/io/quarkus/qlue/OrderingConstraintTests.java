package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.item.SimpleItem;

/**
 * Tests for verifying {@code beforeConsume} and {@code afterProduce} ordering-only constraints.
 */
public class OrderingConstraintTests {

    /**
     * Default constructor for OrderingConstraintTests.
     */
    public OrderingConstraintTests() {
    }

    /**
     * A dummy item used in ordering tests.
     */
    public static final class DummyItem extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyItem() {
        }
    }

    /**
     * Another dummy item used in ordering tests.
     */
    public static final class DummyItem2 extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyItem2() {
        }
    }

    /**
     * Verifies that the {@code beforeConsume} ordering constraint forces a step to complete
     * before any step consuming the target item is initiated.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testBeforeConsumeConstraint() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

        // Step A has a beforeConsume constraint on DummyItem.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                executionLog.add("A");
            }
        }).beforeConsume(DummyItem.class).build();

        // Step B produces DummyItem.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                executionLog.add("B");
                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        // Step C consumes DummyItem.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                executionLog.add("C");
                context.consume(DummyItem.class);
                context.produce(new DummyItem2());
            }
        }).consumes(DummyItem.class).produces(DummyItem2.class).build();

        builder.addFinal(DummyItem2.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());

        // Step A must execute BEFORE Step C because Step A is ordered before anyone consumes DummyItem.
        final int indexA = executionLog.indexOf("A");
        final int indexC = executionLog.indexOf("C");

        assertTrue(indexA != -1, "Step A did not execute");
        assertTrue(indexC != -1, "Step C did not execute");
        assertTrue(indexA < indexC, "Step A did not run before Step C");
    }

    /**
     * Verifies that the {@code afterProduce} ordering constraint forces a step to start
     * after any steps producing the target item are completed.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testAfterProduceConstraint() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

        // Step A produces DummyItem.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                executionLog.add("A");
                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        // Step B has an afterProduce constraint on DummyItem.
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                executionLog.add("B");
                context.produce(new DummyItem2());
            }
        }).afterProduce(DummyItem.class).produces(DummyItem2.class).build();

        builder.addFinal(DummyItem2.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());

        // Step B must execute AFTER Step A because Step B runs after DummyItem is produced.
        final int indexA = executionLog.indexOf("A");
        final int indexB = executionLog.indexOf("B");

        assertTrue(indexA != -1, "Step A did not execute");
        assertTrue(indexB != -1, "Step B did not execute");
        assertTrue(indexA < indexB, "Step B did not run after Step A");
    }
}
