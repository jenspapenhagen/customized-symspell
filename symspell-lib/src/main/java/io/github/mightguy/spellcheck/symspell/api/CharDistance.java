
package io.github.mightguy.spellcheck.symspell.api;

/**
 * Computes a per-character replacement cost between two characters.
 *
 * @author MighTguY
 * @since 6.7
 */
public interface CharDistance {

    /**
     * Returns the replacement cost between two characters as a value between 0 and 1 (both
     * inclusive). 0 means no distance and 1 means maximum distance.
     *
     * @param a the first character
     * @param b the second character
     * @return the replacement cost between {@code a} and {@code b}
     */
    double distance(char a, char b);

}
