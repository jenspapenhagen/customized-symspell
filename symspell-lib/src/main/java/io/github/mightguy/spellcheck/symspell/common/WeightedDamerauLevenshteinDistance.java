
package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.CharDistance;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import java.util.Arrays;

/**
 * DamerauLevenshteinDistance is a string metric for measuring the edit distance between two
 * sequences. Informally, the Damerau–Levenshtein distance between two words is the minimum number
 * of operations (consisting of insertions, deletions or substitutions of a single character, or
 * transposition of two adjacent characters) required to change one word into the other.
 *
 * <p>In this variant of DamerauLevenshteinDistance, it has different weights associated to each
 * action.
 *
 * <p>Performance notes: matching prefixes and suffixes are stripped before the dynamic program,
 * the maximum edit distance variant runs only over the diagonal band that can still reach the
 * requested distance, and the unweighted case uses the bit-parallel optimal string alignment
 * algorithm (Hyyrö 2003), which is roughly ten times faster than the matrix version.
 *
 * @author MighTguY
 * @since 6.7
 */
public class WeightedDamerauLevenshteinDistance implements StringDistance {
    private static final int MAGIC_128 = 128;


    /**
     * Maximum pattern length supported by the single word bit-parallel kernel.
     */
    private static final int MAX_BIT_PARALLEL_LENGTH = 64;

    private static final ThreadLocal<BitMasks> BIT_MASKS = ThreadLocal.withInitial(BitMasks::new);

    // Damerau function variables
    private final double deletionWeight;

    private final double insertionWeight;

    private final double replaceWeight;

    private final double transpositionWeight;

    private final CharDistance charDistance;

    /**
     * True when every edit costs exactly one, which allows using the integer based bit-parallel
     * kernel.
     */
    private final boolean unitCost;

    /**
     * Constructor for Weighted Damerau Levenshtein.
     *
     * @param deletionWeight weight of a deleted character
     * @param insertionWeight weight of an inserted character
     * @param replaceWeight weight of a replaced character
     * @param transpositionWeight weight of a transposition of two adjacent characters
     * @param charDistance optional per character replacement cost, may be {@code null}
     */
    public WeightedDamerauLevenshteinDistance(final double theDeletionWeight, final double theInsertionWeight,
            final double theReplaceWeight, final double theTranspositionWeight, final CharDistance theCharDistance) {
        this.deletionWeight = theDeletionWeight;
        this.insertionWeight = theInsertionWeight;
        this.replaceWeight = theReplaceWeight;
        this.transpositionWeight = theTranspositionWeight;
        this.charDistance = theCharDistance;
        this.unitCost = theCharDistance == null
                && theDeletionWeight == 1.0d
                && theInsertionWeight == 1.0d
                && theReplaceWeight == 1.0d
                && theTranspositionWeight == 1.0d;
    }

    /**
     * Computes the weighted optimal string alignment distance over the given ranges. When
     * {@code width} is smaller than the string length only the diagonal band is evaluated and
     * {@code Double.POSITIVE_INFINITY} is returned as soon as a whole row exceeds
     * {@code maxDistance}.
     */
    private double computeBanded(final String source, final int sourceStart, final int sourceEnd, final String target,
            final int targetStart, final int targetEnd, final int width,
            final boolean useCharDistance, final double maxDistance) {

        final int sourceLength = sourceEnd - sourceStart;
        final int targetLength = targetEnd - targetStart;

        // Integer.MAX_VALUE is used as "no band" marker and would overflow in the bound arithmetic.
        final int band = width >= Integer.MAX_VALUE ? sourceLength + targetLength + 1 : width;

        double[] previousPreviousRow = new double[sourceLength + 1];
        double[] previousRow = new double[sourceLength + 1];
        double[] currentRow = new double[sourceLength + 1];
        Arrays.fill(previousPreviousRow, Double.POSITIVE_INFINITY);
        Arrays.fill(previousRow, Double.POSITIVE_INFINITY);
        Arrays.fill(currentRow, Double.POSITIVE_INFINITY);

        // Row 0: converting an empty source prefix into a target prefix costs inserts only.
        final int initialHi = Math.min(sourceLength, band);
        for (int j = 0; j <= initialHi; j++) {
            previousRow[j] = j * deletionWeight;
        }

        // Smallest value of the last two rows, needed for the sound early exit below.
        double previousRowMinimum = 0;
        double previousPreviousRowMinimum = 0;

        for (int i = 1; i <= targetLength; i++) {
            final char targetChar = target.charAt(targetStart + i - 1);

            final int currentLo = Math.max(1, i - band);
            final int currentHi = Math.min(sourceLength, i + band);
            final int previousLo = Math.max(0, i - 1 - band);
            final int previousHi = Math.min(sourceLength, i - 1 + band);
            final int previousPreviousLo = Math.max(0, i - 2 - band);
            final int previousPreviousHi = Math.min(sourceLength, i - 2 + band);

            currentRow[0] = currentLo == 1 ? i * insertionWeight : Double.POSITIVE_INFINITY;
            if (currentLo > 1) {
                currentRow[currentLo - 1] = Double.POSITIVE_INFINITY;
            }

            double rowMinimum = currentRow[0];

            for (int j = currentLo; j <= currentHi; j++) {
                final char sourceChar = source.charAt(sourceStart + j - 1);
                final double replaceCost = getReplaceCost(targetChar, sourceChar, useCharDistance);

                final double insert = j <= previousHi ? previousRow[j] : Double.POSITIVE_INFINITY;
                final double delete = currentRow[j - 1];
                final double replace = j - 1 >= previousLo && j - 1 <= previousHi
                        ? previousRow[j - 1] : Double.POSITIVE_INFINITY;

                double best = Math.min(insert + insertionWeight,
                        Math.min(delete + deletionWeight, replace + replaceCost));

                if (i > 1 && j > 1
                        && sourceChar == target.charAt(targetStart + i - 2)
                        && targetChar == source.charAt(sourceStart + j - 2)) {
                    final double transpose = j - 2 >= previousPreviousLo && j - 2 <= previousPreviousHi
                            ? previousPreviousRow[j - 2] : Double.POSITIVE_INFINITY;
                    best = Math.min(best, transpose + transpositionWeight);
                }

                currentRow[j] = best;
                if (best < rowMinimum) {
                    rowMinimum = best;
                }
            }

            /*
                A path to the bottom right cell leaves a row either through the next row, or (for a
                transposition) through the row after that. So a row is only hopeless when it, the previous
                row and the row before that plus the cheapest transposition are all above the budget.
             */
            if (Math.min(rowMinimum, Math.min(previousRowMinimum,
                    previousPreviousRowMinimum + transpositionWeight)) > maxDistance) {
                return Double.POSITIVE_INFINITY;
            }

            previousPreviousRowMinimum = previousRowMinimum;
            previousRowMinimum = rowMinimum;

            final double[] scratch = previousPreviousRow;
            previousPreviousRow = previousRow;
            previousRow = currentRow;
            currentRow = scratch;
        }

        return previousRow[sourceLength];
    }

    private double getReplaceCost(final char aI, final char bJ, final boolean useCharDistance) {
        if (aI != bJ && useCharDistance) {
            return replaceWeight * charDistance.distance(aI, bJ);
        } else if (aI != bJ) {
            return replaceWeight;
        } else {
            return 0;
        }
    }

    private static int commonPrefixLength(final String source, final String target) {
        final int limit = Math.min(source.length(), target.length());
        int index = 0;
        while (index < limit && source.charAt(index) == target.charAt(index)) {
            index++;
        }
        return index;
    }

    private static int commonSuffixLength(final String source, final String target, final int prefix) {
        int sourceIndex = source.length() - 1;
        int targetIndex = target.length() - 1;
        int suffix = 0;
        while (sourceIndex >= prefix && targetIndex >= prefix
                && source.charAt(sourceIndex) == target.charAt(targetIndex)) {
            sourceIndex--;
            targetIndex--;
            suffix++;
        }
        return suffix;
    }

    /**
     * Bit-parallel optimal string alignment distance (Hyyrö 2003, single word variant). Returns
     * {@code -1} when the shorter string does not fit into a single 64 bit word.
     */
    private static int bitParallelOsa(final String first, final String second, final int maxDistance) {
        final boolean firstIsPattern = first.length() <= second.length();
        final String pattern = firstIsPattern ? first : second;
        final String text = firstIsPattern ? second : first;

        final int patternLength = pattern.length();
        if (patternLength > MAX_BIT_PARALLEL_LENGTH) {
            return -1;
        }

        final BitMasks masks = BIT_MASKS.get();
        masks.reset();
        for (int i = 0; i < patternLength; i++) {
            masks.add(pattern.charAt(i), i);
        }

        long vp = -1L;
        long vn = 0L;
        long d0 = 0L;
        long previousMask = 0L;
        final long lastBit = 1L << (patternLength - 1);
        int distance = patternLength;

        final int textLength = text.length();
        for (int j = 0; j < textLength; j++) {
            final long mask = masks.mask(text.charAt(j));

            final long transposition = ((~d0 & mask) << 1) & previousMask;
            d0 = ((((mask & vp) + vp) ^ vp) | mask | vn | transposition);

            long hp = vn | ~(d0 | vp);
            long hn = d0 & vp;

            distance += (hp & lastBit) != 0 ? 1 : 0;
            distance -= (hn & lastBit) != 0 ? 1 : 0;

            // Early exit: distance can drop by at most 1 per remaining column.
            if (distance > maxDistance + (textLength - 1 - j)) {
                return -1;
            }

            hp = (hp << 1) | 1L;
            hn <<= 1;
            vp = hn | ~(d0 | hp);
            vn = hp & d0;
            previousMask = mask;
        }

        return distance <= maxDistance ? distance : -1;
    }

    /**
     * Reusable per thread scratch space for the bit-parallel match masks, so the hot path does not
     * allocate a mask table per call.
     */
    private static final class BitMasks {

        private final long[] ascii = new long[MAGIC_128];

        private final char[] otherCharacters = new char[MAX_BIT_PARALLEL_LENGTH];

        private final long[] otherMasks = new long[MAX_BIT_PARALLEL_LENGTH];

        private int otherCount;

        void reset() {
            Arrays.fill(ascii, 0L);
            otherCount = 0;
        }

        void add(final char character, final int bit) {
            final long mask = 1L << bit;
            if (character < MAGIC_128) {
                ascii[character] |= mask;
                return;
            }
            for (int i = 0; i < otherCount; i++) {
                if (otherCharacters[i] == character) {
                    otherMasks[i] |= mask;
                    return;
                }
            }
            otherCharacters[otherCount] = character;
            otherMasks[otherCount] = mask;
            otherCount++;
        }

        long mask(final char character) {
            if (character < MAGIC_128) {
                return ascii[character];
            }
            for (int i = 0; i < otherCount; i++) {
                if (otherCharacters[i] == character) {
                    return otherMasks[i];
                }
            }
            return 0L;
        }
    }


    @Override
    public double getDistance(final String source, final String target) {

        if (source.equals(target)) {
            return 0;
        }

        final int sourceLength = source.length();
        final int targetLength = target.length();

        if (sourceLength == 0) {
            return targetLength * insertionWeight;
        }

        if (targetLength == 0) {
            return sourceLength * deletionWeight;
        }

        if (unitCost) {
            final int distance = bitParallelOsa(source, target, Integer.MAX_VALUE);
            if (distance >= 0) {
                return distance;
            }
        }

        final boolean useCharDistance = (charDistance != null && sourceLength == targetLength);
        final int prefix = commonPrefixLength(source, target);
        final int suffix = commonSuffixLength(source, target, prefix);

        if (prefix + suffix >= sourceLength || prefix + suffix >= targetLength) {
            // One string is a prefix/suffix trimmed to nothing, so only inserts or deletes remain.
            final int remainingSource = sourceLength - prefix - suffix;
            final int remainingTarget = targetLength - prefix - suffix;
            return remainingTarget * insertionWeight + remainingSource * deletionWeight;
        }

        return computeBanded(source, prefix, sourceLength - suffix,
                target, prefix, targetLength - suffix, Integer.MAX_VALUE, useCharDistance,
                Double.POSITIVE_INFINITY);
    }



    @Override
    public double getDistance(final String w1, final String w2, final double maxEditDistance) {

        if (maxEditDistance < 0) {
            return -1;
        }

        if (w1.equals(w2)) {
            return 0;
        }

        final int sourceLength = w1.length();
        final int targetLength = w2.length();
        final double minIndelWeight = Math.min(deletionWeight, insertionWeight);

        if (sourceLength == 0) {
            final double distance = targetLength * insertionWeight;
            return distance <= maxEditDistance ? distance : -1;
        }

        if (targetLength == 0) {
            final double distance = sourceLength * deletionWeight;
            return distance <= maxEditDistance ? distance : -1;
        }

        /*
            The edit distance can never be smaller than the weighted length difference, so most
            candidates are rejected without touching the matrix at all.
         */
        if (minIndelWeight > 0
                && Math.abs(sourceLength - targetLength) * minIndelWeight > maxEditDistance) {
            return -1;
        }

        if (unitCost) {
            final int distance = bitParallelOsa(w1, w2, (int) Math.floor(maxEditDistance));
            if (distance >= 0) {
                return distance;
            }
            // bitParallelOsa returns -1 either because the pattern is too long for the
            // bit-parallel kernel or because the distance exceeds maxEditDistance.
            // Only the former requires falling through to the dynamic program.
            if (Math.min(w1.length(), w2.length()) <= MAX_BIT_PARALLEL_LENGTH) {
                return -1;
            }
        }

        final int prefix = commonPrefixLength(w1, w2);
        final int suffix = commonSuffixLength(w1, w2, prefix);
        final int sourceStart = prefix;
        final int sourceEnd = sourceLength - suffix;
        final int targetStart = prefix;
        final int targetEnd = targetLength - suffix;

        if (sourceStart >= sourceEnd) {
            final double distance = (targetEnd - targetStart) * insertionWeight;
            return distance <= maxEditDistance ? distance : -1;
        }

        if (targetStart >= targetEnd) {
            final double distance = (sourceEnd - sourceStart) * deletionWeight;
            return distance <= maxEditDistance ? distance : -1;
        }

        /*
            Only cells with |i - j| <= width can still be reached with the allowed budget, because every
            insert or delete moves one cell away from the diagonal and costs at least the cheaper of the
            two weights.
         */
        final int width;
        if (minIndelWeight <= 0) {
            width = Integer.MAX_VALUE;
        } else {
            width = (int) Math.floor(maxEditDistance / minIndelWeight);
        }

        final boolean useCharDistance = (charDistance != null && sourceLength == targetLength);
        final double distance = computeBanded(w1, sourceStart, sourceEnd, w2, targetStart, targetEnd, width,
                useCharDistance, maxEditDistance);
        if (distance > maxEditDistance) {
            return -1;
        }
        return distance;
    }


}
