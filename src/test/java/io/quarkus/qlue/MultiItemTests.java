package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.qlue.item.MultiItem;
import io.quarkus.qlue.item.SimpleItem;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Tests for verifying {@link MultiItem} behavior, natural sorting, and custom comparator sorting.
 */
public class MultiItemTests {

    /**
     * Default constructor for MultiItemTests.
     */
    public MultiItemTests() {
    }

    /**
     * A dummy simple item used to signify step completion in tests.
     */
    public static final class DummyFinishedItem extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyFinishedItem() {
        }
    }

    /**
     * A comparable multi-item implementation for testing.
     */
    public static final class ComparableItem extends MultiItem implements Comparable<ComparableItem> {
        private final int value;

        /**
         * Constructs a ComparableItem with a value.
         *
         * @param value the item value
         */
        public ComparableItem(int value) {
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

        /**
         * Compares this item with another.
         *
         * @param other the other item to compare to
         * @return the comparison result
         */
        @Override
        public int compareTo(ComparableItem other) {
            return Integer.compare(this.value, other.value);
        }
    }

    /**
     * A non-comparable multi-item implementation for testing.
     */
    public static final class NonComparableItem extends MultiItem {
        private final int value;

        /**
         * Constructs a NonComparableItem with a value.
         *
         * @param value the item value
         */
        public NonComparableItem(int value) {
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
     * Verifies that multi-items which are Comparable are automatically sorted in natural order when produced.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testMultiItemComparableSorting() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(new ComparableItem(20));
                context.produce(new ComparableItem(5));
                context.produce(new ComparableItem(10));
            }
        }).produces(ComparableItem.class).build();

        builder.addFinal(ComparableItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        final Success success = result.asSuccess();
        final List<ComparableItem> items = success.consumeMulti(ComparableItem.class);

        assertEquals(3, items.size());
        assertEquals(5, items.get(0).value());
        assertEquals(10, items.get(1).value());
        assertEquals(20, items.get(2).value());
    }

    /**
     * Verifies that multi-items can be sorted using a custom comparator during step consumption.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testStepContextCustomComparator() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(new NonComparableItem(20));
                context.produce(new NonComparableItem(5));
                context.produce(new NonComparableItem(10));
            }
        }).produces(NonComparableItem.class).build();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                final List<NonComparableItem> items = context.consumeMulti(NonComparableItem.class,
                        new Comparator<NonComparableItem>() {
                            @Override
                            public int compare(NonComparableItem a, NonComparableItem b) {
                                return Integer.compare(b.value(), a.value());
                            }
                        });
                assertEquals(3, items.size());
                assertEquals(20, items.get(0).value());
                assertEquals(10, items.get(1).value());
                assertEquals(5, items.get(2).value());
                context.produce(new DummyFinishedItem());
            }
        }).consumes(NonComparableItem.class).produces(DummyFinishedItem.class).build();

        builder.addFinal(DummyFinishedItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertTrue(result.isSuccess());
    }

    /**
     * Verifies that multi-items can be sorted descending using a custom comparator on success.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testMultiItemCustomComparator() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(new NonComparableItem(20));
                context.produce(new NonComparableItem(5));
                context.produce(new NonComparableItem(10));
            }
        }).produces(NonComparableItem.class).build();

        builder.addFinal(NonComparableItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        final Success success = result.asSuccess();

        final List<NonComparableItem> items = success.consumeMulti(NonComparableItem.class);
        items.sort(new Comparator<NonComparableItem>() {
            @Override
            public int compare(NonComparableItem a, NonComparableItem b) {
                return Integer.compare(b.value(), a.value());
            }
        });

        assertEquals(3, items.size());
        assertEquals(20, items.get(0).value());
        assertEquals(10, items.get(1).value());
        assertEquals(5, items.get(2).value());
    }
}
