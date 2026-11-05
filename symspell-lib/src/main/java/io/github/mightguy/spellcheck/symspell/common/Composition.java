
package io.github.mightguy.spellcheck.symspell.common;

/**
 * Result of the word segmentation: the segmented string, the corrected string and the scores.
  *
 * @author MighTguY
 * @since 6.7
 */
public class Composition {
    private static final int MAGIC_31 = 31;
    private static final int MAGIC_32 = 32;


    private String segmentedString;
    private String correctedString;
    private int distanceSum;
    private double logProbSum;

    /**
     * Empty composition, used as a placeholder while filling the dynamic programming table.
     */
    public Composition() {
        // nothing to initialize
    }

    /**
     * @param segmentedString the input split into words
     * @param correctedString the corrected and joined words
     * @param distanceSum     sum of the edit distances of all words
     * @param logProbSum      sum of the log probabilities of all words
     */
    public Composition(final String theSegmentedString, final String theCorrectedString, final int theDistanceSum,
            final double theLogProbSum) {
        this.segmentedString = theSegmentedString;
        this.correctedString = theCorrectedString;
        this.distanceSum = theDistanceSum;
        this.logProbSum = theLogProbSum;
    }

    /**
     * @return the input split into words
     */
    public String getSegmentedString() {
        return segmentedString;
    }

    /**
     * @param segmentedString the input split into words
     */
    public void setSegmentedString(final String theSegmentedString) {
        this.segmentedString = theSegmentedString;
    }

    /**
     * @return the corrected and joined words
     */
    public String getCorrectedString() {
        return correctedString;
    }

    /**
     * @param correctedString the corrected and joined words
     */
    public void setCorrectedString(final String theCorrectedString) {
        this.correctedString = theCorrectedString;
    }

    /**
     * @return sum of the edit distances of all words
     */
    public int getDistanceSum() {
        return distanceSum;
    }

    /**
     * @param distanceSum sum of the edit distances of all words
     */
    public void setDistanceSum(final int theDistanceSum) {
        this.distanceSum = theDistanceSum;
    }

    /**
     * @return sum of the log probabilities of all words
     */
    public double getLogProbSum() {
        return logProbSum;
    }

    /**
     * @param logProbSum sum of the log probabilities of all words
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
        result = MAGIC_31 * result + (correctedString != null ? correctedString.hashCode() : 0);
        result = MAGIC_31 * result + distanceSum;
        final long logProbBits = Double.doubleToLongBits(logProbSum);
        result = MAGIC_31 * result + (int) (logProbBits ^ (logProbBits >>> MAGIC_32));
        return result;
    }

    private static boolean equal(final Object first, final Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
