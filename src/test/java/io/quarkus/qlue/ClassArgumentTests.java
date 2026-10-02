package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.SimpleClassItem;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Tests for verifying {@link SimpleClassItem} and {@link MultiClassItem} behavior with class arguments.
 */
public class ClassArgumentTests {

    /**
     * Default constructor for ClassArgumentTests.
     */
    public ClassArgumentTests() {
    }

    /**
     * A simple class-parameterized item for testing.
     */
    public static final class ParameterizedSimpleItem extends SimpleClassItem<Object> {
        private final String message;

        /**
         * Constructs a ParameterizedSimpleItem.
         *
         * @param message the message
         */
        public ParameterizedSimpleItem(String message) {
            this.message = message;
        }

        /**
         * Gets the message.
         *
         * @return the message
         */
        public String message() {
            return message;
        }
    }

    /**
     * A multi class-parameterized item for testing.
     */
    public static final class ParameterizedMultiItem extends MultiClassItem<Object> {
        private final int value;

        /**
         * Constructs a ParameterizedMultiItem.
         *
         * @param value the value
         */
        public ParameterizedMultiItem(int value) {
            this.value = value;
        }

        /**
         * Gets the value.
         *
         * @return the value
         */
        public int value() {
            return value;
        }
    }

    /**
     * Verifies that simple class-argument items are correctly isolated and routed by their parameterized class arguments.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testSimpleClassArgumentIsolation() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(String.class, new ParameterizedSimpleItem("Hello String"));
            }
        }).produces((Class) ParameterizedSimpleItem.class, String.class).build();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(Integer.class, new ParameterizedSimpleItem("Hello Integer"));
            }
        }).produces((Class) ParameterizedSimpleItem.class, Integer.class).build();

        builder.addFinal((Class) ParameterizedSimpleItem.class, String.class);
        builder.addFinal((Class) ParameterizedSimpleItem.class, Integer.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        if (result.isFailure()) {
            System.err.println("SIMPLE FAILURE PROBLEMS: " + result.asFailure().getProblems());
        }
        assertTrue(result.isSuccess());
        final Success success = result.asSuccess();

        final ParameterizedSimpleItem stringItem = (ParameterizedSimpleItem) success
                .consume((Class) ParameterizedSimpleItem.class, String.class);
        final ParameterizedSimpleItem integerItem = (ParameterizedSimpleItem) success
                .consume((Class) ParameterizedSimpleItem.class, Integer.class);

        assertEquals("Hello String", stringItem.message());
        assertEquals("Hello Integer", integerItem.message());
    }

    /**
     * Verifies that multi class-argument items are correctly accumulated, isolated, and routed by their parameterized class
     * arguments.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testMultiClassArgumentIsolation() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(String.class, new ParameterizedMultiItem(10));
                context.produce(String.class, new ParameterizedMultiItem(20));
            }
        }).produces((Class) ParameterizedMultiItem.class, String.class).build();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(Integer.class, new ParameterizedMultiItem(100));
            }
        }).produces((Class) ParameterizedMultiItem.class, Integer.class).build();

        builder.addFinal((Class) ParameterizedMultiItem.class, String.class);
        builder.addFinal((Class) ParameterizedMultiItem.class, Integer.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        if (result.isFailure()) {
            System.err.println("MULTI FAILURE PROBLEMS: " + result.asFailure().getProblems());
        }
        assertTrue(result.isSuccess());
        final Success success = result.asSuccess();

        final List<ParameterizedMultiItem> stringItems = (List) success.consumeMulti((Class) ParameterizedMultiItem.class,
                String.class);
        final List<ParameterizedMultiItem> integerItems = (List) success.consumeMulti((Class) ParameterizedMultiItem.class,
                Integer.class);

        assertEquals(2, stringItems.size());
        assertEquals(10, stringItems.get(0).value());
        assertEquals(20, stringItems.get(1).value());

        assertEquals(1, integerItems.size());
        assertEquals(100, integerItems.get(0).value());
    }
}
