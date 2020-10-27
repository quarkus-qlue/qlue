package io.quarkus.qlue.item;

import io.quarkus.qlue._private.Messages;

/**
 * An empty item. Empty items carry no data and may be used, for example, for ordering and for
 * running steps which don't otherwise produce anything.
 */
public abstract class EmptyItem extends Item {
    protected EmptyItem() {
        throw Messages.log.emptyItem();
    }
}
