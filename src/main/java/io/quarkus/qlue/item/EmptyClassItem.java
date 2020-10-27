package io.quarkus.qlue.item;

import io.quarkus.qlue._private.Messages;

/**
 * An empty item which is identified by its type and the corresponding class object.
 * Empty items carry no data and may be used, for example, for ordering and for running steps which don't otherwise
 * produce anything.
 */
public abstract class EmptyClassItem<U> extends ClassItem<U> {
    protected EmptyClassItem() {
        throw Messages.log.emptyItem();
    }
}
