package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.item.SimpleItem;

/**
 */
public class BasicTests {

    public static final class DummyItem extends SimpleItem {
    }

    public static final class DummyItem2 extends SimpleItem {
    }

    @Test
    public void testSimple() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        final AtomicBoolean ran = new AtomicBoolean();
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                ran.set(true);
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        builder.addFinal(DummyItem.class);
        Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertTrue(ran.get());
        assertTrue(result.isSuccess());
        assertNotNull(((Success) result).consume(DummyItem.class));
    }

    @Test
    public void testFailure() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                throw new NoClassDefFoundError();
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        builder.addFinal(DummyItem.class);
        Chain chain = builder.build();
        Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertTrue(result.isFailure());
    }

    @Test
    public void testFailure2() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                throw new NoClassDefFoundError();
            }
        });

        final AtomicBoolean ran = new AtomicBoolean();
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                ran.set(true);
            }
        });
        stepBuilder.consumes(DummyItem.class);
        stepBuilder.produces(DummyItem2.class);
        stepBuilder.build();
        builder.addFinal(DummyItem2.class);
        Chain chain = builder.build();
        Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertFalse(ran.get());
        assertTrue(result.isFailure());
    }

    @Test
    public void testLinked() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                assertNotNull(context.consume(DummyItem.class));
                context.produce(new DummyItem2());
            }
        });
        stepBuilder.consumes(DummyItem.class);
        stepBuilder.produces(DummyItem2.class);
        stepBuilder.build();
        builder.addFinal(DummyItem2.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertTrue(result.isSuccess());
        assertNotNull(((Success) result).consume(DummyItem2.class));
    }

    @Test
    public void testInitial() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addInitial(DummyItem.class);
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                assertNotNull(context.consume(DummyItem.class));
                context.produce(new DummyItem2());
            }
        });
        stepBuilder.consumes(DummyItem.class);
        stepBuilder.produces(DummyItem2.class);
        stepBuilder.build();
        builder.addFinal(DummyItem2.class);
        final Chain chain = builder.build();
        final ExecutionBuilder eb = chain.createExecutionBuilder();
        eb.produce(DummyItem.class, new DummyItem());
        final Result result = eb.execute(Runnable::run);
        assertTrue(result.isSuccess());
        assertNotNull(((Success) result).consume(DummyItem2.class));
    }

    @Test
    public void testPruning() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        final AtomicBoolean ran = new AtomicBoolean();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                assertNotNull(context.consume(DummyItem.class));
                context.produce(new DummyItem2());
                ran.set(true);
            }
        });
        stepBuilder.consumes(DummyItem.class);
        stepBuilder.produces(DummyItem2.class);
        stepBuilder.build();
        builder.addFinal(DummyItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);
        assertTrue(result.isSuccess());
        assertNotNull(((Success) result).consume(DummyItem.class));
        assertFalse(ran.get());
    }

    @Test
    public void testCircular() {
        final ChainBuilder builder = Chain.builder();
        builder.addFinal(DummyItem.class);
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.consume(DummyItem2.class);
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.consumes(DummyItem2.class);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.consume(DummyItem.class);
                context.produce(new DummyItem2());
            }
        });
        stepBuilder.produces(DummyItem2.class);
        stepBuilder.consumes(DummyItem.class);
        stepBuilder.build();
        try {
            builder.build();
            fail("Expected exception");
        } catch (ChainBuildException expected) {
            // ok
        }
    }

    @Test
    public void testDuplicate() {
        final ChainBuilder builder = Chain.builder();
        builder.addFinal(DummyItem.class);
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        try {
            builder.build();
            fail("Expected exception");
        } catch (ChainBuildException expected) {
            // ok
        }
    }

    @Test
    public void testDuplicateOverridable() {
        final ChainBuilder builder = Chain.builder();
        builder.addFinal(DummyItem.class);
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class, ProduceFlag.OVERRIDABLE);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class, ProduceFlag.OVERRIDABLE);
        stepBuilder.build();
        try {
            builder.build();
            fail("Expected exception");
        } catch (ChainBuildException expected) {
            // ok
        }
    }

    @Test
    public void testOverride() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addFinal(DummyItem.class);
        StepBuilder stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class, ProduceFlag.OVERRIDABLE);
        stepBuilder.build();
        stepBuilder = builder.addRawStep(new Consumer<StepContext>() {
            public void accept(final StepContext context) {
                context.produce(new DummyItem());
            }
        });
        stepBuilder.produces(DummyItem.class);
        stepBuilder.build();
        builder.build();
    }
}
