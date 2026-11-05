package io.github.mightguy.spellcheck.symspell.impl;

import java.util.Arrays;

/**
 * Minimal open addressing set for primitive integers, used to deduplicate term ids inside a single
 * lookup. Each slot packs the generation stamp and the value into one {@code long}, so an add needs
 * a single array access. Entries are invalidated by bumping a generation counter, which makes
 * {@link #clear()} O(1) and avoids boxing as well as object allocation.
 *
 * @author MighTguY
 * @since 6.7
 */
final class IntHashSet {
    private static final int MAGIC_5 = 5;

    private static final int MAGIC_3 = 3;

    private static final int MAGIC_16 = 16;

    private static final int MAGIC_32 = 32;

    private static final int DEFAULT_CAPACITY = 256;

    private static final long MAGIC_0XFFFFFFFF = 0xFFFFFFFFL;

    /**
     * High 32 bits hold the generation stamp, low 32 bits the value.
     */
    private long[] entries;

    private int generation = 1;

    private int mask;

    private int size;

    private int growLimit;

    IntHashSet() {
        allocate(DEFAULT_CAPACITY);
    }

    /**
     * Adds the value and returns {@code true} if it was not present yet.
     *
     * @param value the value to add
     * @return {@code true} if the value was not part of the current generation
     */
    boolean add(final int value) {
        if (size >= growLimit) {
            resize(entries.length * 2);
        }
        int slot = mix(value) & mask;
        final long stamp = (long) generation << MAGIC_32;
        while (true) {
            final long entry = entries[slot];
            if ((int) (entry >>> MAGIC_32) != generation) {
                entries[slot] = stamp | (value & MAGIC_0XFFFFFFFF);
                size++;
                return true;
            }
            if ((int) entry == value) {
                return false;
            }
            slot = (slot + 1) & mask;
        }
    }

    /**
     * Invalidates all entries in constant time.
     */
    void clear() {
        size = 0;
        generation++;
        if (generation == Integer.MAX_VALUE) {
            Arrays.fill(entries, 0L);
            generation = 1;
        }
    }

    private static int mix(final int value) {
        final int mixed = value * 0x9E3779B1;
        return mixed ^ (mixed >>> MAGIC_16);
    }

    private void resize(final int capacity) {
        final long[] oldEntries = entries;
        final int oldGeneration = generation;
        allocate(capacity);
        for (int i = 0; i < oldEntries.length; i++) {
            final long entry = oldEntries[i];
            if ((int) (entry >>> MAGIC_32) == oldGeneration) {
                final int value = (int) entry;
                int slot = mix(value) & mask;
                while ((int) (entries[slot] >>> MAGIC_32) == generation) {
                    slot = (slot + 1) & mask;
                }
                entries[slot] = ((long) generation << MAGIC_32) | (value & MAGIC_0XFFFFFFFF);
                size++;
            }
        }
    }

    private void allocate(final int capacity) {
        entries = new long[capacity];
        mask = capacity - 1;
        size = 0;
        growLimit = capacity * MAGIC_3 / MAGIC_5;
    }
}
