package io.github.mightguy.spellcheck.symspell.impl;

final class StringIntMap {
    private static final double LOAD_FACTOR = 0.6;
    private static final int HASH_MULTIPLIER = 0x9E3779B1;
    private static final int HASH_MIX_SHIFT = 16;


    private static final int INITIAL_CAPACITY = 1024;

    private String[] keys;
    private int[] values;
    private int[] hashes;
    private int size;
    private int mask;
    private int threshold;

    StringIntMap() {
        allocate(INITIAL_CAPACITY);
    }

    private void allocate(final int capacity) {
        keys = new String[capacity];
        values = new int[capacity];
        hashes = new int[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
        size = 0;
    }

    private static int mix(final int hash) {
        return hash * HASH_MULTIPLIER ^ (hash >>> HASH_MIX_SHIFT);
    }

    int get(final String key) {
        if (key == null) {
            return -1;
        }
        final int hash = key.hashCode();
        final int mixed = mix(hash);
        int index = mixed & mask;
        while (keys[index] != null) {
            if (hashes[index] == hash && keys[index].equals(key)) {
                return values[index];
            }
            index = (index + 1) & mask;
        }
        return -1;
    }

    void put(final String key, final int value) {
        if (size >= threshold) {
            resize();
        }
        final int hash = key.hashCode();
        final int mixed = mix(hash);
        int index = mixed & mask;
        while (keys[index] != null) {
            if (hashes[index] == hash && keys[index].equals(key)) {
                values[index] = value;
                return;
            }
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        hashes[index] = hash;
        size++;
    }

    private void resize() {
        final String[] oldKeys = keys;
        final int[] oldValues = values;
        final int[] oldHashes = hashes;
        final int oldCapacity = keys.length;
        allocate(oldCapacity << 1);
        for (int i = 0; i < oldCapacity; i++) {
            if (oldKeys[i] != null) {
                final int hash = oldHashes[i];
                final int mixed = mix(hash);
                int index = mixed & mask;
                while (keys[index] != null) {
                    index = (index + 1) & mask;
                }
                keys[index] = oldKeys[i];
                values[index] = oldValues[i];
                hashes[index] = hash;
                size++;
            }
        }
    }

    void clear() {
        allocate(INITIAL_CAPACITY);
    }

    int size() {
        return size;
    }
}