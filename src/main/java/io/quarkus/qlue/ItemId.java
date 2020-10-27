package io.quarkus.qlue;

import java.util.Objects;

import io.quarkus.qlue.item.Item;
import io.quarkus.qlue.item.MultiClassItem;
import io.quarkus.qlue.item.MultiItem;
import io.smallrye.common.constraint.Assert;

/**
 */
final class ItemId {
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

    boolean isMulti() {
        return MultiItem.class.isAssignableFrom(itemType) || MultiClassItem.class.isAssignableFrom(itemType);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ItemId && equals((ItemId) obj);
    }

    boolean equals(ItemId obj) {
        return this == obj || obj != null && itemType == obj.itemType && itemArg == obj.itemArg;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemType, itemArg);
    }

    @Override
    public String toString() {
        if (itemArg != null) {
            return itemType + "<" + itemArg + ">";
        } else {
            return itemType.toString();
        }
    }

    Class<? extends Item> getType() {
        return itemType;
    }
}
