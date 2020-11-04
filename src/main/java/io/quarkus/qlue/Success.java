package io.quarkus.qlue;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.qlue._private.Messages;
import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.MultiItem;
import io.quarkus.qlue.item.SimpleClassItem;
import io.quarkus.qlue.item.SimpleItem;

/**
 * The final result of a successful operation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Success extends Result {
    private final ConcurrentHashMap<ItemId, Item> simpleItems;
    private final ConcurrentHashMap<ItemId, List<Item>> multiItems;
    private final long nanos;

    Success(final ConcurrentHashMap<ItemId, Item> simpleItems,
            final ConcurrentHashMap<ItemId, List<Item>> multiItems,
            final long nanos) {
        this.simpleItems = simpleItems;
        this.multiItems = multiItems;
        this.nanos = nanos;
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     * @throws ClassCastException if the cast failed
     */
    public <T extends SimpleItem> T consume(Class<T> type) {
        final ItemId itemId = new ItemId(type);
        final Object item = simpleItems.get(itemId);
        if (item == null) {
            throw Messages.log.undeclaredItem(itemId);
        }
        return type.cast(item);
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws ClassCastException if the cast failed
     */
    public <T extends SimpleItem> T consumeOptional(Class<T> type) {
        final ItemId itemId = new ItemId(type);
        final Object item = simpleItems.get(itemId);
        if (item == null) {
            return null;
        }
        return type.cast(item);
    }

    /**
     * Consume all of the values produced for the named item.
     *
     * @param type the item element type (must not be {@code null})
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}
     */
    public <T extends MultiItem> List<T> consumeMulti(Class<T> type) {
        final ItemId itemId = new ItemId(type);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final List<T> items = (List<T>) (List) multiItems.get(itemId);
        if (items == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(items);
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}, or if {@code type} is
     *         {@code null}
     * @throws ClassCastException if the cast failed
     */
    public <U, T extends SimpleClassItem<U>> T consume(Class<T> type, Class<? extends U> argument) {
        final ItemId itemId = new ItemId(type, argument);
        final Object item = simpleItems.get(itemId);
        if (item == null) {
            throw Messages.log.undeclaredItem(itemId);
        }
        return type.cast(item);
    }

    /**
     * Consume the value produced for the named item.
     *
     * @param type the item type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced item (may be {@code null})
     * @throws ClassCastException if the cast failed
     */
    public <U, T extends SimpleClassItem<U>> T consumeOptional(Class<T> type, Class<? extends U> argument) {
        final ItemId itemId = new ItemId(type, argument);
        final Object item = simpleItems.get(itemId);
        if (item == null) {
            return null;
        }
        return type.cast(item);
    }

    /**
     * Consume all of the values produced for the named item.
     *
     * @param type the item element type (must not be {@code null})
     * @param argument the item argument (must not be {@code null})
     * @param <U> the upper bound of the argument type
     * @param <T> the item type
     * @return the produced items (may be empty, will not be {@code null})
     * @throws IllegalArgumentException if this step was not declared to consume {@code type}
     */
    public <U, T extends MultiClassItem<U>> List<T> consumeMulti(Class<T> type, Class<? extends U> argument) {
        final ItemId itemId = new ItemId(type, argument);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final List<T> items = (List<T>) (List) multiItems.get(itemId);
        if (items == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(items);
    }

    /**
     * Get the amount of elapsed time from the time the operation was initiated to the time it was completed.
     *
     * @return the duration
     */
    public Duration getDuration() {
        return Duration.of(nanos, ChronoUnit.NANOS);
    }

    /**
     * Close all the resultant resources, logging any failures.
     */
    public void closeAll() throws RuntimeException {
        for (Item obj : simpleItems.values()) {
            if (obj instanceof AutoCloseable)
                try {
                    ((AutoCloseable) obj).close();
                } catch (Exception e) {
                    Messages.log.closeFailed(e, obj);
                }
        }
        for (List<? extends Item> list : multiItems.values()) {
            for (Item obj : list) {
                if (obj instanceof AutoCloseable)
                    try {
                        ((AutoCloseable) obj).close();
                    } catch (Exception e) {
                        Messages.log.closeFailed(e, obj);
                    }
            }
        }
    }

    public boolean isSuccess() {
        return true;
    }

    public Success asSuccess() {
        return this;
    }
}
