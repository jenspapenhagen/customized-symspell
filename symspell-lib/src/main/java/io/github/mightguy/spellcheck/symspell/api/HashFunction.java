
package io.github.mightguy.spellcheck.symspell.api;

/**
 * Hashes byte arrays and terms for the delete index.
 *
 * @author MighTguY
 * @since 6.7
 */
public interface HashFunction {

    /**
     * Return the hash of the bytes as long.
     *
     * @param bytes the bytes to be hashed
     * @return the generated hash value
     */
    public long hash(byte[] bytes);


    /**
     * Return the hash of the bytes as long.
     *
     * @param data the String to be hashed
     * @return the generated  hash value
     */
    public long hash(String data);


}
