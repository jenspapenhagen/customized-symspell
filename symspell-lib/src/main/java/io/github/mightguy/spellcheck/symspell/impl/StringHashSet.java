package io.github.mightguy.spellcheck.symspell.impl;

/**
 * Minimal open addressing set for strings, used for the candidate and suggestion deduplication
 * inside a single lookup. {@link java.util.HashSet} allocates a node object per entry and rehashes
 * the strings; this set stores the (cached) string hash next to the reference and can be reused
 * across lookups without clearing the whole table: entries are invalidated by bumping a
 * generation counter, so {@link #clear()} is O(1).
 */
final class StringHashSet {
    private static final int LOAD_FACTOR_DENOMINATOR = 5;
    private static final int LOAD_FACTOR_NUMERATOR = 3;
    private static final int HASH_MIX_SHIFT = 16;


    private static final int DEFAULT_CAPACITY = 128;

    private String[] values;

    private int[] hashes;

    private int[] generations;

    private int generation = 1;

    private int mask;

    private int size;

    StringHashSet() {
        allocate(DEFAULT_CAPACITY);
    }

    /**
     * Adds the value and returns {@code true} if it was not present yet.
     */
    boolean add(final String value) {
        final int hash = value.hashCode();
        int slot = mix(hash) & mask;
        while (true) {
            if (generations[slot] != generation) {
                values[slot] = value;
                hashes[slot] = hash;
                generations[slot] = generation;
                size++;
                if (size * LOAD_FACTOR_DENOMINATOR > values.length * LOAD_FACTOR_NUMERATOR) {
                    resize(values.length * 2);
                }
                return true;
            }
            if (hashes[slot] == hash && values[slot].equals(value)) {
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
            java.util.Arrays.fill(generations, 0);
            java.util.Arrays.fill(values, null);
            generation = 1;
        }
    }

    private static int mix(final int hash) {
        final int mixed = hash * 0x9E3779B1;
        return mixed ^ (mixed >>> HASH_MIX_SHIFT);
    }

    private void resize(final int capacity) {
        final String[] oldValues = values;
        final int[] oldHashes = hashes;
        final int[] oldGenerations = generations;
        final int oldGeneration = generation;
        allocate(capacity);
        for (int i = 0; i < oldValues.length; i++) {
            if (oldGenerations[i] == oldGeneration) {
                final String value = oldValues[i];
                final int hash = oldHashes[i];
                int slot = mix(hash) & mask;
                while (generations[slot] == generation) {
                    slot = (slot + 1) & mask;
                }
                values[slot] = value;
                hashes[slot] = hash;
                generations[slot] = generation;
                size++;
            }
        }
    }

    private void allocate(final int capacity) {
        values = new String[capacity];
        hashes = new int[capacity];
        generations = new int[capacity];
        mask = capacity - 1;
        size = 0;
    }
}
