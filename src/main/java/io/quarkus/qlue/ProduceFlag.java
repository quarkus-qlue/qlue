package io.quarkus.qlue;

import io.quarkus.qlue.item.SimpleItem;

/**
 */
public enum ProduceFlag {
    /**
     * Only produce this item weakly: if only weak items produced by a step are consumed, the step will not be included.
     */
    WEAK,
    /**
     * Only produce this {@link SimpleItem} if no other steps produce it.
     */
    OVERRIDABLE,
}
