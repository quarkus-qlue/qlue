package io.quarkus.qlue;

import static io.quarkus.qlue._private.Messages.log;

import java.util.NoSuchElementException;
import java.util.Objects;

import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.MultiItem;
import io.smallrye.common.constraint.Assert;

/**
 * An identifier for a build item.
 */
public final class ItemId {
    private final Class<? extends Item> itemType;
    private final Object itemArg;

    ItemId(final Class<? extends Item> itemType, Object itemArg) {
        Assert.checkNotNullParam("itemType", itemType);
        Assert.checkNotNullParam("itemArg", itemArg);
        this.itemType = itemType;
        this.itemArg = itemArg;
    }

    ItemId(final Class<? extends Item> itemType) {
        Assert.checkNotNullParam("itemType", itemType);
        this.itemType = itemType;
        this.itemArg = null;
    }

    /**
     * {@return <code>true</code> if the item supports multiplicity}
     */
    public boolean isMulti() {
        return MultiItem.class.isAssignableFrom(itemType) || MultiClassItem.class.isAssignableFrom(itemType);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ItemId ii && equals(ii);
    }

    public boolean equals(ItemId obj) {
        return this == obj || obj != null && itemType == obj.itemType && itemArg == obj.itemArg;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemType, itemArg);
    }

    /**
     * {@return a string representation of the item}
     */
    @Override
    public String toString() {
        if (itemArg != null) {
            return itemType + "<" + itemArg + ">";
        } else {
            return itemType.toString();
        }
    }

    /**
     * {@return the item's type class}
     */
    public Class<? extends Item> itemType() {
        return itemType;
    }

    /**
     * {@return the item's <code>Class</code>-typed argument}
     *
     * @throws NoSuchElementException if the item has no argument
     * @throws ClassCastException if the item's argument exists but is not of type {@code Class}
     */
    public Class<?> itemClassArgument() {
        Object itemArg = this.itemArg;
        if (itemArg instanceof Class<?> clz) {
            return clz;
        }
        if (itemArg == null) {
            throw log.noItemArgument(this);
        }
        throw log.wrongItemArgumentType(this, Class.class, itemArg.getClass());
    }
}
