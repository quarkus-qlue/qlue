package io.quarkus.qlue;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.annotation.ForClass;
import io.quarkus.qlue.annotation.Step;
import io.quarkus.qlue.item.SimpleItem;
import io.quarkus.qlue.item.StepClassItem;

/**
 *
 */
public class ReflectTests {
    public static final class RanItem extends SimpleItem {
        final AtomicBoolean ran = new AtomicBoolean();
    }

    public static final class DummyItem extends SimpleItem {
    }

    public static final class DummyItem2 extends SimpleItem {
    }

    public static final class StepClass {

        @Step
        public DummyItem2 doSomething(DummyItem input1, RanItem input2) {
            input2.ran.set(true);
            return new DummyItem2();
        }
    }

    @Test
    public void testInject() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addStepClass(StepClass.class);
        builder.addInitial(RanItem.class);
        builder.addInitial(DummyItem.class);
        builder.addFinal(DummyItem2.class);
        builder.addFinal(RanItem.class);
        Chain chain = builder.build();
        ExecutionBuilder executionBuilder = chain.createExecutionBuilder();
        executionBuilder.produce(new RanItem());
        executionBuilder.produce(new DummyItem());
        final Result result = executionBuilder.execute(Runnable::run);
        assertTrue(result.isSuccess());
        Success success = result.asSuccess();
        assertTrue(success.consume(RanItem.class).ran.get());
        assertNotNull(success.consume(DummyItem2.class));
    }

    @Test
    public void testAnonymous() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();
        builder.addStepObject(new Object() {
            @Step
            public DummyItem getTheItem() {
                return new DummyItem();
            }

            @Step
            public void getTheOtherItem(Consumer<DummyItem2> consumer) {
                consumer.accept(new DummyItem2());
            }
        }, lookup());
        builder.addFinal(DummyItem.class);
        builder.addFinal(DummyItem2.class);
        Chain chain = builder.build();
        ExecutionBuilder executionBuilder = chain.createExecutionBuilder();
        final Result result = executionBuilder.execute(Runnable::run);
        assertTrue(result.isSuccess());
        Success success = result.asSuccess();
        assertNotNull(success.consume(DummyItem.class));
        assertNotNull(success.consume(DummyItem2.class));
    }

    public static final class InjectSelf {
        @Test
        public void checkItOut(@ForClass(InjectSelf.class) StepClassItem item) {
            assertSame(this, item.getInstance());
        }
    }

    @Test
    public void testInjectSelf() throws ChainBuildException {
        ChainBuilder builder = Chain.builder();
        builder.addStepClass(InjectSelf.class);
        builder.addFinal(StepClassItem.class, InjectSelf.class);
        Chain chain = builder.build();
        ExecutionBuilder executionBuilder = chain.createExecutionBuilder();
        final Result result = executionBuilder.execute(Runnable::run);
        assertTrue(result.isSuccess());
        Success success = result.asSuccess();
        assertNotNull(success.consume(StepClassItem.class, InjectSelf.class));
    }
}
