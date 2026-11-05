
package io.github.mightguy.spellcheck.symspell.impl;

import java.util.Arrays;

/**
 * Minimal open addressing map from primitive {@code long} keys to {@code int} values. Used for the
 * delete hash index, so the lookup hot path does not allocate a boxed {@link Long} per candidate
 * and does not pay for {@link java.util.HashMap} nodes.
 */
final class LongIntMap {
    private static final int MAGIC_16 = 16;
    private static final int MAGIC_32 = 32;


    private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

    /**
     * Keys are stored incremented by one, so zero can mark an unused slot.
     */
    private long[] keys;

    private int[] values;

    private int size;

    private int mask;

    LongIntMap() {
        allocate(MAGIC_16);
    }

    /**
     * Returns the value for the given key or {@code -1} when the key is absent.
     */
    int get(final long key) {
        final long storedKey = key + 1;
        int slot = slotOf(storedKey);
        while (true) {
            final long current = keys[slot];
            if (current == 0L) {
                return -1;
            }
            if (current == storedKey) {
                return values[slot];
            }
            slot = (slot + 1) & mask;
        }
    }

    /**
     * Stores the value for the given key, replacing a previous value if present.
     */
    void put(final long key, final int value) {
        final long storedKey = key + 1;
        int slot = slotOf(storedKey);
        while (true) {
            final long current = keys[slot];
            if (current == 0L) {
                keys[slot] = storedKey;
                values[slot] = value;
                size++;
                if (size * 2 > keys.length) {
                    resize(keys.length * 2);
                }
                return;
            }
            if (current == storedKey) {
                values[slot] = value;
                return;
            }
            slot = (slot + 1) & mask;
        }
    }

    int getMask() {
        return mask;
    }

    long getKey(final int slot) {
        return keys[slot];
    }

    int getValue(final int slot) {
        return values[slot];
    }

    void clear() {
        Arrays.fill(keys, 0L);
        size = 0;
    }

    int size() {
        return size;
    }

    private int slotOf(final long storedKey) {
        return ((int) (storedKey >>> MAGIC_32) ^ (int) storedKey) & mask;
    }

    private void resize(final int capacity) {
        final long[] oldKeys = keys;
        final int[] oldValues = values;
        allocate(capacity);
        for (int i = 0; i < oldKeys.length; i++) {
            final long key = oldKeys[i];
            if (key != 0L) {
                int slot = slotOf(key);
                while (keys[slot] != 0L) {
                    slot = (slot + 1) & mask;
                }
                keys[slot] = key;
                values[slot] = oldValues[i];
                size++;
            }
        }
    }

    private void allocate(final int capacity) {
        keys = new long[capacity];
        values = new int[capacity];
        mask = capacity - 1;
        size = 0;
    }
}
