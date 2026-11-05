package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.HashFunction;

/**
 * Experimental allocation-free 64 bit hash over UTF-16 code units.
  *
 * @author MighTguY
 * @since 6.7
 */
public class Utf16HashFunction implements HashFunction {
    private static final long GOLDEN_RATIO_MIX = 0x9E3779B185EBCA87L;
    private static final long CHAR_MASK = 0xffffL;
    private static final int CHAR_PAIR_SHIFT = 16;
    private static final long PAIR_MIX_MULTIPLIER = 0xC2B2AE3D27D4EB4FL;
    private static final int AVALANCHE_SHIFT = 33;
    private static final int BLOCK_ROTATION = 27;
    private static final int BLOCK_MULTIPLIER = 5;
    private static final int BLOCK_INCREMENT = 0x52DCE729;
    private static final long FINAL_MIX_MULTIPLIER = 0xFF51AFD7ED558CCDL;
    private static final long FINAL_MIX_MULTIPLIER_SECOND = 0xC4CEB9FE1A85EC53L;


    /**
     * Hashes the given bytes by decoding them as UTF-8 and hashing the resulting string.
     *
     * @param bytes the bytes to hash
     * @return the hash value
     */
    public long hash(final byte[] bytes) {
        return hash(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Hashes the given string over its UTF-16 code units.
     *
     * @param data the string to hash
     * @return the hash value
     */
    public long hash(final String data) {
        final int length = data.length();
        long h = GOLDEN_RATIO_MIX ^ length;

        int i = 0;
        for (; i + 1 < length; i += 2) {
            long v = (data.charAt(i) & CHAR_MASK)
                    | ((data.charAt(i + 1) & CHAR_MASK) << CHAR_PAIR_SHIFT);

            v *= PAIR_MIX_MULTIPLIER;
            v ^= v >>> AVALANCHE_SHIFT;
            v *= GOLDEN_RATIO_MIX;

            h ^= v;
            h = Long.rotateLeft(h, BLOCK_ROTATION) * BLOCK_MULTIPLIER + BLOCK_INCREMENT;
        }

        if (i < length) {
            h ^= data.charAt(i) & CHAR_MASK;
        }

        return mix(h);
    }

    /**
     * Hashes the first {@code length} characters of the given array over their UTF-16 code units.
     * This is the allocation free variant used by the lookup hot path, which builds delete
     * candidates in a reusable buffer.
     *
     * @param data the characters to hash
     * @param length the number of characters to hash
     * @return the hash value
     */
    public long hash(final char[] data, final int length) {
        long h = GOLDEN_RATIO_MIX ^ length;

        int i = 0;
        for (; i + 1 < length; i += 2) {
            long v = (data[i] & CHAR_MASK)
                    | ((data[i + 1] & CHAR_MASK) << CHAR_PAIR_SHIFT);

            v *= PAIR_MIX_MULTIPLIER;
            v ^= v >>> AVALANCHE_SHIFT;
            v *= GOLDEN_RATIO_MIX;

            h ^= v;
            h = Long.rotateLeft(h, BLOCK_ROTATION) * BLOCK_MULTIPLIER + BLOCK_INCREMENT;
        }

        if (i < length) {
            h ^= data[i] & CHAR_MASK;
        }

        return mix(h);
    }

    private static long mix(final long value) {
        long h = value;
        h ^= h >>> AVALANCHE_SHIFT;
        h *= FINAL_MIX_MULTIPLIER;
        h ^= h >>> AVALANCHE_SHIFT;
        h *= FINAL_MIX_MULTIPLIER_SECOND;
        h ^= h >>> AVALANCHE_SHIFT;
        return h;
    }
}