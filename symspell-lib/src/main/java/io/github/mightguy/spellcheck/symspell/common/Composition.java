
package io.github.mightguy.spellcheck.symspell.common;

/**
 * Result of the word segmentation: the segmented string, the corrected string and the scores.
 *
 * @author MighTguY
 * @since 6.7
 */
public class Composition {
    private static final int HASH_MULTIPLIER = 31;
    private static final int LONG_SHIFT = 32;


    private String segmentedString;
    private String correctedString;
    private int distanceSum;
    private double logProbSum;

    /**
     * Creates an empty composition, used as a placeholder while filling the dynamic programming
     * table.
     */
    public Composition() {
        // nothing to initialize
    }

    /**
     * Creates a composition with the given segmentation result.
     *
     * @param theSegmentedString the input split into words
     * @param theCorrectedString the corrected and joined words
     * @param theDistanceSum sum of the edit distances of all words
     * @param theLogProbSum sum of the log probabilities of all words
     */
    public Composition(final String theSegmentedString, final String theCorrectedString, final int theDistanceSum,
            final double theLogProbSum) {
        this.segmentedString = theSegmentedString;
        this.correctedString = theCorrectedString;
        this.distanceSum = theDistanceSum;
        this.logProbSum = theLogProbSum;
    }

    /**
     * Returns the input split into words.
     *
     * @return the segmented string
     */
    public String getSegmentedString() {
        return segmentedString;
    }

    /**
     * Sets the input split into words.
     *
     * @param theSegmentedString the segmented string
     */
    public void setSegmentedString(final String theSegmentedString) {
        this.segmentedString = theSegmentedString;
    }

    /**
     * Returns the corrected and joined words.
     *
     * @return the corrected string
     */
    public String getCorrectedString() {
        return correctedString;
    }

    /**
     * Sets the corrected and joined words.
     *
     * @param theCorrectedString the corrected string
     */
    public void setCorrectedString(final String theCorrectedString) {
        this.correctedString = theCorrectedString;
    }

    /**
     * Returns the sum of the edit distances of all words.
     *
     * @return the distance sum
     */
    public int getDistanceSum() {
        return distanceSum;
    }

    /**
     * Sets the sum of the edit distances of all words.
     *
     * @param theDistanceSum the distance sum
     */
    public void setDistanceSum(final int theDistanceSum) {
        this.distanceSum = theDistanceSum;
    }

    /**
     * Returns the sum of the log probabilities of all words.
     *
     * @return the log probability sum
     */
    public double getLogProbSum() {
        return logProbSum;
    }

    /**
     * Sets the sum of the log probabilities of all words.
     *
     * @param theLogProbSum the log probability sum
     */
    public void setLogProbSum(final double theLogProbSum) {
        this.logProbSum = theLogProbSum;
    }

    @Override
    public String toString() {
        return "Composition(segmentedString=" + segmentedString
                + ", correctedString=" + correctedString
                + ", distanceSum=" + distanceSum
                + ", logProbSum=" + logProbSum + ")";
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Composition)) {
            return false;
        }
        final Composition that = (Composition) other;
        return distanceSum == that.distanceSum
                && Double.compare(logProbSum, that.logProbSum) == 0
                && equal(segmentedString, that.segmentedString)
                && equal(correctedString, that.correctedString);
    }

    @Override
    public int hashCode() {
        int result = segmentedString != null ? segmentedString.hashCode() : 0;
        result = HASH_MULTIPLIER * result + (correctedString != null ? correctedString.hashCode() : 0);
        result = HASH_MULTIPLIER * result + distanceSum;
        final long logProbBits = Double.doubleToLongBits(logProbSum);
        result = HASH_MULTIPLIER * result + (int) (logProbBits ^ (logProbBits >>> LONG_SHIFT));
        return result;
    }

    private static boolean equal(final Object first, final Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
