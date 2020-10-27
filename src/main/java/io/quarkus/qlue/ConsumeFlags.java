package io.quarkus.qlue;

import io.smallrye.common.constraint.Assert;

/**
 * Flags which can be set on consume declarations.
 */
public final class ConsumeFlags {
    final int bits;

    private ConsumeFlags(final int bits) {
        this.bits = bits;
    }

    public static ConsumeFlags value(final int bits) {
        return values[bits & (1 << enumValues.length) - 1];
    }

    private static final ConsumeFlag[] enumValues = ConsumeFlag.values();
    private static final ConsumeFlags[] values;

    static {
        final ConsumeFlags[] flags = new ConsumeFlags[1 << ConsumeFlag.values().length];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = new ConsumeFlags(i);
        }
        values = flags;
    }

    public static final ConsumeFlags NONE = values[0];

    static int bitOf(ConsumeFlag flag) {
        return 1 << Assert.checkNotNullParam("flag", flag).ordinal();
    }

    public static ConsumeFlags of(ConsumeFlag flag) {
        return values[bitOf(flag)];
    }

    public ConsumeFlags with(final ConsumeFlags flags) {
        return values[bits | Assert.checkNotNullParam("flags", flags).bits];
    }

    public ConsumeFlags with(final ConsumeFlag flag) {
        return values[bits | bitOf(flag)];
    }

    public ConsumeFlags without(final ConsumeFlag flag) {
        return values[bits & ~bitOf(flag)];
    }

    public boolean contains(final ConsumeFlag flag) {
        return (bits & bitOf(flag)) != 0;
    }
}
