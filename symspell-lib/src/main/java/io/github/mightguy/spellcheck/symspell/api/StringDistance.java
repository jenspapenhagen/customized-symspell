package io.github.mightguy.spellcheck.symspell.api;

/**
 * Computes the edit distance between two terms.
 *
 * @author MighTguY
 * @since 6.7
 */
public interface StringDistance {

    /**
     * Returns the edit distance between two words.
     *
     * @param w1 the first word
     * @param w2 the second word
     * @return the distance; {@code 0} means the words are equal
     */
    double getDistance(String w1, String w2);

    /**
     * Returns the edit distance between two words, or a negative value when it exceeds the given
     * maximum.
     *
     * <p>Implementations may stop early once the maximum is exceeded, which makes this overload
     * cheaper than {@link #getDistance(String, String)} for filtering.
     *
     * @param w1 the first word
     * @param w2 the second word
     * @param maxEditDistance the maximum distance to compute; must be non-negative
     * @return the distance, or a negative value if it is greater than {@code maxEditDistance}
     */
    double getDistance(String w1, String w2, double maxEditDistance);
}
