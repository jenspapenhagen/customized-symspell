
package io.github.mightguy.spellcheck.symspell.common;

import java.io.Serializable;
import java.util.Comparator;

/**
 * A suggested correction with its term, edit distance and frequency.
 *
 * <p>Instances are ordered by distance ascending and then by frequency descending.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SuggestionItem
        implements Comparator<SuggestionItem>, Comparable<SuggestionItem>, Serializable {
    private static final double DISTANCE_EPSILON = 0.001f;
    private static final int HASH_MULTIPLIER = 31;
    private static final int LONG_SHIFT = 32;


    /**
     * The suggested correctly spelled word.
     */
    private String term;

    /**
     * Edit distance between searched for word and suggestion.
     */
    private double distance;

    /**
     * Frequency of suggestion in the dictionary (a measure of how common the word is).
     */
    private double count;

    /**
     * COnstructor  for SuggestionItem.
     * @param term
     * @param distance
     * @param count
     */
    public SuggestionItem(final String theTerm, final double theDistance, final double theCount) {
        this.term = theTerm;
        this.distance = theDistance;
        this.count = theCount;
    }

    /**
     * final similarity.
     */
    private double score;

    /**
     * @return the suggested correctly spelled word
     */
    public String getTerm() {
        return term;
    }

    /**
     * @param term the suggested correctly spelled word
     */
    public void setTerm(final String theTerm) {
        this.term = theTerm;
    }

    /**
     * @return edit distance between searched for word and suggestion
     */
    public double getDistance() {
        return distance;
    }

    /**
     * @param distance edit distance between searched for word and suggestion
     */
    public void setDistance(final double theDistance) {
        this.distance = theDistance;
    }

    /**
     * @return frequency of the suggestion in the dictionary
     */
    public double getCount() {
        return count;
    }

    /**
     * @param count frequency of the suggestion in the dictionary
     */
    public void setCount(final double theCount) {
        this.count = theCount;
    }

    /**
     * @return the final similarity score
     */
    public double getScore() {
        return score;
    }

    /**
     * @param score the final similarity score
     */
    public void setScore(final double theScore) {
        this.score = theScore;
    }

    /**
     * Comparison to use in Sorting: Prefernce given to distance, and if distance is same then count.
     */
    @Override
    public int compareTo(final SuggestionItem other) {
        if (SpellHelper.isEqualDouble(this.distance, other.distance, DISTANCE_EPSILON)) {
            return Double.compare(other.count, this.count);
        }
        return Double.compare(this.distance, other.distance);
    }

    @Override
    public int compare(final SuggestionItem suggestItem, final SuggestionItem suggestItem2) {
        return suggestItem.compareTo(suggestItem2);
    }

    @Override
    public String toString() {
        return "SuggestionItem(term=" + term
                + ", distance=" + distance
                + ", count=" + count
                + ", score=" + score + ")";
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SuggestionItem)) {
            return false;
        }
        final SuggestionItem that = (SuggestionItem) other;
        return Double.compare(distance, that.distance) == 0
                && Double.compare(count, that.count) == 0
                && Double.compare(score, that.score) == 0
                && (term == null ? that.term == null : term.equals(that.term));
    }

    @Override
    public int hashCode() {
        int result = term != null ? term.hashCode() : 0;
        final long distanceBits = Double.doubleToLongBits(distance);
        result = HASH_MULTIPLIER * result + (int) (distanceBits ^ (distanceBits >>> LONG_SHIFT));
        final long countBits = Double.doubleToLongBits(count);
        result = HASH_MULTIPLIER * result + (int) (countBits ^ (countBits >>> LONG_SHIFT));
        final long scoreBits = Double.doubleToLongBits(score);
        result = HASH_MULTIPLIER * result + (int) (scoreBits ^ (scoreBits >>> LONG_SHIFT));
        return result;
    }

}
