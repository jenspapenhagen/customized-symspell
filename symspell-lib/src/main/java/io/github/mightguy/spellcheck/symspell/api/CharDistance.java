
package io.github.mightguy.spellcheck.symspell.api;

/**
 * Computes a per-character replacement cost between two characters.
 *
 * @author MighTguY
 * @since 6.7
 */
public interface CharDistance {

    /**
     * returns a value between 0 and 1 (both inclusive). 0 for no distance and 1 for maximum
     * distance.
     *
     * @return distance value
     */
    double distance(char a, char b);

}
