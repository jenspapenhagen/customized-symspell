package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.HashFunction;

/**
 * Experimental allocation-free 64 bit hash over UTF-16 code units.
  *
 * @author MighTguY
 * @since 6.7
 */
public class Utf16HashFunction implements HashFunction {
    private static final long MAGIC_0X9E3779B185EBCA87 = 0x9E3779B185EBCA87L;
    private static final long MAGIC_0Xffff = 0xffffL;
    private static final int MAGIC_16 = 16;
    private static final long MAGIC_0XC2B2AE3D27D4EB4F = 0xC2B2AE3D27D4EB4FL;
    private static final int MAGIC_33 = 33;
    private static final int MAGIC_27 = 27;
    private static final int MAGIC_5 = 5;
    private static final int MAGIC_0X52DCE729 = 0x52DCE729;
    private static final long MAGIC_0XFF51AFD7ED558CCD = 0xFF51AFD7ED558CCDL;
    private static final long MAGIC_0XC4CEB9FE1A85EC53 = 0xC4CEB9FE1A85EC53L;


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
        long h = MAGIC_0X9E3779B185EBCA87 ^ length;

        int i = 0;
        for (; i + 1 < length; i += 2) {
            long v = (data.charAt(i) & MAGIC_0Xffff)
                    | ((data.charAt(i + 1) & MAGIC_0Xffff) << MAGIC_16);

            v *= MAGIC_0XC2B2AE3D27D4EB4F;
            v ^= v >>> MAGIC_33;
            v *= MAGIC_0X9E3779B185EBCA87;

            h ^= v;
            h = Long.rotateLeft(h, MAGIC_27) * MAGIC_5 + MAGIC_0X52DCE729;
        }

        if (i < length) {
            h ^= data.charAt(i) & MAGIC_0Xffff;
        }

        h ^= h >>> MAGIC_33;
        h *= MAGIC_0XFF51AFD7ED558CCD;
        h ^= h >>> MAGIC_33;
        h *= MAGIC_0XC4CEB9FE1A85EC53;
        h ^= h >>> MAGIC_33;

        return h;
    }
}