package io.quarkus.qlue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.qlue.item.SimpleItem;

/**
 * Tests for verifying {@link AttachmentKey} and the state storage limits in {@link StepContext}.
 */
public class AttachmentTests {

    /**
     * Default constructor for AttachmentTests.
     */
    public AttachmentTests() {
    }

    /**
     * A dummy simple item used in attachment tests.
     */
    public static final class DummyItem extends SimpleItem {
        /**
         * Default constructor.
         */
        public DummyItem() {
        }
    }

    /**
     * Verifies that a step context can register, check, and retrieve up to two attachments successfully,
     * and that attempting to register a third attachment correctly throws an {@link IllegalStateException}.
     *
     * @throws ChainBuildException if building the chain fails
     */
    @Test
    public void testStepContextAttachments() throws ChainBuildException {
        final ChainBuilder builder = Chain.builder();

        final AttachmentKey<String> key1 = new AttachmentKey<>();
        final AttachmentKey<Integer> key2 = new AttachmentKey<>();
        final AttachmentKey<Boolean> key3 = new AttachmentKey<>();

        builder.addRawStep(new Consumer<StepContext>() {
            @Override
            public void accept(final StepContext context) {
                // Assert initially empty
                assertNull(context.getAttachment(key1));
                assertFalse(context.hasAttachment(key1));

                // Put first attachment
                assertNull(context.putAttachment(key1, "Attachment 1"));
                assertTrue(context.hasAttachment(key1));
                assertEquals("Attachment 1", context.getAttachment(key1));

                // Put if absent on existing key
                assertEquals("Attachment 1", context.putAttachmentIfAbsent(key1, "New Value"));
                assertEquals("Attachment 1", context.getAttachment(key1));

                // Put second attachment
                assertNull(context.putAttachment(key2, 42));
                assertTrue(context.hasAttachment(key2));
                assertEquals(Integer.valueOf(42), context.getAttachment(key2));

                // Attempt to put a third attachment (violating the 2-slot low-overhead limit)
                assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.putAttachment(key3, Boolean.TRUE);
                    }
                });

                assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        context.putAttachmentIfAbsent(key3, Boolean.TRUE);
                    }
                });

                context.produce(new DummyItem());
            }
        }).produces(DummyItem.class).build();

        builder.addFinal(DummyItem.class);
        final Chain chain = builder.build();
        final Result result = chain.createExecutionBuilder().execute(Runnable::run);

        assertTrue(result.isSuccess());
    }
}
