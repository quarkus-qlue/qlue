package io.quarkus.qlue.item;

import static io.quarkus.qlue._private.Messages.log;

import java.lang.reflect.Modifier;

/**
 * An item which can be produced or consumed. Any item
 * which implements {@link AutoCloseable} will be automatically closed when the execution
 * is completed, unless it is explicitly marked as a final execution result in which case closure is
 * the responsibility of whomever invoked the execution.
 * <p>
 * Resources should be fine-grained as possible, ideally describing only one aspect of the overall process.
 */
public abstract class Item {
    Item() {
        final Class<? extends Item> clazz = getClass();
        if (clazz.getTypeParameters().length != 0) {
            throw log.genericNotAllowed(clazz);
        }
        if (!Modifier.isFinal(clazz.getModifiers())) {
            throw log.itemsMustBeLeafs(clazz);
        }
    }
}
