package io.quarkus.qlue;

import io.smallrye.common.constraint.Assert;

/**
 * Flags which can be set on consume declarations.
 */
public final class ProduceFlags {
    final int bits;

    private ProduceFlags(final int bits) {
        this.bits = bits;
    }

    public static ProduceFlags value(final int bits) {
        return values[bits & (1 << enumValues.length) - 1];
    }

    private static final ProduceFlag[] enumValues = ProduceFlag.values();
    private static final ProduceFlags[] values;

    static {
        final ProduceFlags[] flags = new ProduceFlags[1 << ProduceFlag.values().length];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = new ProduceFlags(i);
        }
        values = flags;
    }

    public static final ProduceFlags NONE = values[0];

    static int bitOf(ProduceFlag flag) {
        return 1 << Assert.checkNotNullParam("flag", flag).ordinal();
    }

    public static ProduceFlags of(ProduceFlag flag) {
        return values[bitOf(flag)];
    }

    public ProduceFlags with(final ProduceFlags flags) {
        return values[bits | Assert.checkNotNullParam("flags", flags).bits];
    }

    public ProduceFlags with(final ProduceFlag flag) {
        return values[bits | bitOf(flag)];
    }

    public ProduceFlags without(final ProduceFlag flag) {
        return values[bits & ~bitOf(flag)];
    }

    public boolean contains(final ProduceFlag flag) {
        return (bits & bitOf(flag)) != 0;
    }
}
