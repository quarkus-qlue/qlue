package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.item.ClassItem;
import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.SimpleClassItem;
import io.quarkus.qlue.item.SimpleItem;

/**
 * Precondition failure and validation tests for verifying Qlue's fail-fast design.
 */
public class PreconditionFailureTests {

    /**
     * Default constructor for PreconditionFailureTests.
     */
    public PreconditionFailureTests() {
    }

    /** A simple item used in precondition tests. */
    public static final class DummyItem extends SimpleItem {
        /** Default constructor. */
        public DummyItem() {
        }
    }

    /** A class-parameterized item used in precondition tests. */
    public static final class ParameterizedItem extends SimpleClassItem<Object> {
        /** Default constructor. */
        public ParameterizedItem() {
        }
    }

    /**
     * Verifies that attempting to produce or consume items on a {@link StepContext} outside of the active,
     * running step state throws an {@link IllegalStateException}.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testStatePreconditionFailures() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        final StepContext[] capturedContext = new StepContext[1];

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                capturedContext[0] = context;
                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        builder.addFinal(DummyItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());

        final StepContext context = capturedContext[0];
        // After execution is completed, state is COMPLETE, not RUNNING. So these should fail fast.
        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                context.consume(DummyItem.class);
            }
        });

        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                context.produce(new DummyItem());
            }
        });
    }

    /**
     * Verifies that consuming an undeclared item throws an {@link IllegalArgumentException}.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testUndeclaredItemAccess() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                // Consuming DummyItem which is NOT declared as consumed in this step!
                assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.consume(DummyItem.class);
                    }
                });
                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        builder.addFinal(DummyItem.class);
        final Chain chain = builder.build();
        chain.createExecutionBuilder().execute(Runnable::run);
    }

    /**
     * Verifies that producing a non-multi item multiple times in the same context throws an {@link IllegalArgumentException}.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testDuplicateSingleProduction() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
                // Producing it a second time in the same step context
                assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.produce(new DummyItem());
                    }
                });
            }
        }).produces(DummyItem.class).build();

        builder.addFinal(DummyItem.class);
        final Chain chain = builder.build();
        chain.createExecutionBuilder().execute(Runnable::run);
    }

    /**
     * Verifies that producing/consuming a class-argument item class without a class argument throws an
     * {@link IllegalArgumentException}.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testClassItemArgumentPreconditions() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                // ParameterizedItem extends SimpleClassItem, which is a ClassItem.
                // Producing it without a class argument class should throw IllegalArgumentException.
                assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.produce(new ParameterizedItem());
                    }
                });

                assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.produce((Class) ParameterizedItem.class, (Item) new ParameterizedItem());
                    }
                });

                assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.consume((Class) ParameterizedItem.class);
                    }
                });

                context.produce(String.class, new ParameterizedItem());
            }
        }).produces((Class) ParameterizedItem.class, String.class).build();

        builder.addFinal((Class) ParameterizedItem.class, String.class);
        final Chain chain = builder.build();
        chain.createExecutionBuilder().execute(Runnable::run);
    }
}
