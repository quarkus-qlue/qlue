package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.qlue.annotation.Step;
import io.quarkus.qlue.item.SimpleItem;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * Tests for verifying conditional execution of steps via the {@code when} and {@code unless} annotation filters.
 */
public class ConditionalStepTests {

    /**
     * Default constructor for ConditionalStepTests.
     */
    public ConditionalStepTests() {
    }

    /**
     * A boolean supplier that always returns {@code true}.
     */
    public static final class TrueSupplier implements BooleanSupplier {
        /** Default constructor. */
        public TrueSupplier() {
        }

        @Override
        public boolean getAsBoolean() {
            return true;
        }
    }

    /**
     * A boolean supplier that always returns {@code false}.
     */
    public static final class FalseSupplier implements BooleanSupplier {
        /** Default constructor. */
        public FalseSupplier() {
        }

        @Override
        public boolean getAsBoolean() {
            return false;
        }
    }

    /**
     * A dummy simple item signifying step completion in tests.
     */
    public static final class DummyFinishedItem extends SimpleItem {
        /** Default constructor. */
        public DummyFinishedItem() {
        }
    }

    /**
     * A step class with a step method that should execute because of {@code when = TrueSupplier.class}.
     */
    public static final class WhenTrueStep {
        private static final AtomicBoolean ran = new AtomicBoolean();

        /** Default constructor. */
        public WhenTrueStep() {
        }

        /**
         * Executes the step.
         *
         * @return a completion item
         */
        @Step(when = TrueSupplier.class)
        public DummyFinishedItem execute() {
            ran.set(true);
            return new DummyFinishedItem();
        }
    }

    /**
     * A step class with a step method that should NOT execute because of {@code when = FalseSupplier.class}.
     */
    public static final class WhenFalseStep {
        private static final AtomicBoolean ran = new AtomicBoolean();

        /** Default constructor. */
        public WhenFalseStep() {
        }

        /**
         * Executes the step.
         *
         * @return a completion item
         */
        @Step(when = FalseSupplier.class)
        public DummyFinishedItem execute() {
            ran.set(true);
            return new DummyFinishedItem();
        }
    }

    /**
     * A step class with a step method that should NOT execute because of {@code unless = TrueSupplier.class}.
     */
    public static final class UnlessTrueStep {
        private static final AtomicBoolean ran = new AtomicBoolean();

        /** Default constructor. */
        public UnlessTrueStep() {
        }

        /**
         * Executes the step.
         *
         * @return a completion item
         */
        @Step(unless = TrueSupplier.class)
        public DummyFinishedItem execute() {
            ran.set(true);
            return new DummyFinishedItem();
        }
    }

    /**
     * A step class with a step method that should execute because of {@code unless = FalseSupplier.class}.
     */
    public static final class UnlessFalseStep {
        private static final AtomicBoolean ran = new AtomicBoolean();

        /** Default constructor. */
        public UnlessFalseStep() {
        }

        /**
         * Executes the step.
         *
         * @return a completion item
         */
        @Step(unless = FalseSupplier.class)
        public DummyFinishedItem execute() {
            ran.set(true);
            return new DummyFinishedItem();
        }
    }

    /**
     * Verifies that {@code @Step(when = TrueSupplier.class)} executes and {@code @Step(when = FalseSupplier.class)} is skipped.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testWhenConditionalFilters() throws ChainBuildException {
        WhenTrueStep.ran.set(false);
        WhenFalseStep.ran.set(false);

        final ChainBuilder builder = Chain.builder();
        builder.addStepClass(WhenTrueStep.class);
        builder.addStepClass(WhenFalseStep.class);
        builder.addFinal(DummyFinishedItem.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        assertTrue(WhenTrueStep.ran.get());
        assertFalse(WhenFalseStep.ran.get());
        assertNotNull(result.asSuccess().consume(DummyFinishedItem.class));
    }

    /**
     * Verifies that {@code @Step(unless = FalseSupplier.class)} executes and {@code @Step(unless = TrueSupplier.class)} is
     * skipped.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testUnlessConditionalFilters() throws ChainBuildException {
        UnlessTrueStep.ran.set(false);
        UnlessFalseStep.ran.set(false);

        final ChainBuilder builder = Chain.builder();
        builder.addStepClass(UnlessTrueStep.class);
        builder.addStepClass(UnlessFalseStep.class);
        builder.addFinal(DummyFinishedItem.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        assertTrue(UnlessFalseStep.ran.get());
        assertFalse(UnlessTrueStep.ran.get());
        assertNotNull(result.asSuccess().consume(DummyFinishedItem.class));
    }
}
