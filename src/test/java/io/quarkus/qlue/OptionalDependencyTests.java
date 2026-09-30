package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.annotation.Step;
import io.quarkus.qlue.item.SimpleItem;

/**
 * Tests for verifying {@link Optional} dependency and injection behavior.
 */
public class OptionalDependencyTests {

    /**
     * Default constructor for OptionalDependencyTests.
     */
    public OptionalDependencyTests() {
    }

    /**
     * A dummy simple item used in optional tests.
     */
    public static final class DummyItem extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyItem() {
        }
    }

    /**
     * A dummy simple item signifying successful step execution.
     */
    public static final class DummyFinishedItem extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyFinishedItem() {
        }
    }

    /**
     * A test step class that expects an optional {@link DummyItem} to be injected.
     */
    public static final class OptionalInjectionStep {
        private static final AtomicBoolean ran = new AtomicBoolean();
        private static final AtomicBoolean present = new AtomicBoolean();

        /**
         * Default constructor.
         */
        public OptionalInjectionStep() {
        }

        /**
         * Resets state variables before running tests.
         */
        public static void reset() {
            ran.set(false);
            present.set(false);
        }

        /**
         * Executes the step.
         *
         * @param maybeItem the optional injected item
         * @return a completion item
         */
        @Step
        public DummyFinishedItem execute(Optional<DummyItem> maybeItem) {
            ran.set(true);
            if (maybeItem.isPresent()) {
                present.set(true);
            }
            return new DummyFinishedItem();
        }
    }

    /**
     * Verifies that if an optional dependency is present (i.e. produced), it is successfully injected as Optional.of(item).
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testOptionalDependencyPresent() throws ChainBuildException {
        OptionalInjectionStep.reset();
        final ChainBuilder builder = Chain.builder();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        builder.addStepClass(OptionalInjectionStep.class);
        builder.addFinal(DummyFinishedItem.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        assertTrue(OptionalInjectionStep.ran.get());
        assertTrue(OptionalInjectionStep.present.get());
        assertNotNull(result.asSuccess().consume(DummyFinishedItem.class));
    }

    /**
     * Verifies that if an optional dependency is absent (i.e. not produced), the chain still builds and runs,
     * injecting Optional.empty() into the step.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testOptionalDependencyAbsent() throws ChainBuildException {
        OptionalInjectionStep.reset();
        final ChainBuilder builder = Chain.builder();

        // No step produces DummyItem, but we still expect OptionalInjectionStep to execute.
        builder.addStepClass(OptionalInjectionStep.class);
        builder.addFinal(DummyFinishedItem.class);

        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
        assertTrue(OptionalInjectionStep.ran.get());
        assertFalse(OptionalInjectionStep.present.get());
        assertNotNull(result.asSuccess().consume(DummyFinishedItem.class));
    }
}
