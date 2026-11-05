package io.github.mightguy.spellcheck.symspell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import java.util.Random;
import org.junit.Test;

/**
 * Randomized cross check of the optimized weighted distance implementation against a plain,
 * unoptimized full matrix reference. Covers the bit-parallel unit cost path, the banded weighted
 * path and the maximum distance early termination.
 */
public class WeightedDistanceFuzzTest {

    private static final double EPSILON = 1e-9;

    private static final double[][] WEIGHTS = {
            {1, 1, 1, 1},
            {1, 1, 0.7, 0.8},
            {0.8, 1.01, 0.89, 0.7},
            {1.2, 0.6, 1.5, 0.35},
            {0.5, 0.5, 2, 0.9},
    };

    @Test
    public void optimizedDistanceMatchesReferenceMatrix() {
        Random random = new Random(0x5EED5EEDL);
        String alphabet = "abcd";
        for (double[] weights : WEIGHTS) {
            WeightedDamerauLevenshteinDistance distance = new WeightedDamerauLevenshteinDistance(
                    weights[0], weights[1], weights[2], weights[3], null);
            for (int round = 0; round < 400; round++) {
                String first = randomWord(random, alphabet, random.nextInt(9));
                String second = randomWord(random, alphabet, random.nextInt(9));
                double expected = referenceDistance(first, second, weights);
                assertEquals("weights=" + java.util.Arrays.toString(weights)
                                + " first=" + first + " second=" + second,
                        expected, distance.getDistance(first, second), EPSILON);

                for (double max : new double[]{0, 0.5, 1, 1.5, 2, 3}) {
                    double optimised = distance.getDistance(first, second, max);
                    if (optimised < 0) {
                        assertTrue("distance " + expected + " must exceed " + max,
                                expected > max + EPSILON);
                    } else {
                        assertEquals("weights=" + java.util.Arrays.toString(weights) + " first=" + first
                                + " second=" + second + " max=" + max, expected, optimised, EPSILON);
                    }
                }
            }
        }
    }

    @Test
    public void bitParallelPathMatchesReferenceForLongerWords() {
        Random random = new Random(0xC0FFEEL);
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        WeightedDamerauLevenshteinDistance distance =
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null);
        for (int round = 0; round < 300; round++) {
            String first = randomWord(random, alphabet, 1 + random.nextInt(80));
            String second = randomWord(random, alphabet, 1 + random.nextInt(80));
            double[] weights = {1, 1, 1, 1};
            assertEquals(expectedMessage(first, second),
                    referenceDistance(first, second, weights), distance.getDistance(first, second), 0);
        }
    }

    private static String expectedMessage(String first, String second) {
        return "first=" + first + " second=" + second;
    }

    /**
     * Straightforward full matrix optimal string alignment implementation, used as reference.
     */
    private static double referenceDistance(String source, String target, double[] weights) {
        int sourceLength = source.length();
        int targetLength = target.length();
        double[][] matrix = new double[targetLength + 1][sourceLength + 1];
        for (int j = 0; j <= sourceLength; j++) {
            matrix[0][j] = j * weights[0];
        }
        for (int i = 0; i <= targetLength; i++) {
            matrix[i][0] = i * weights[1];
        }
        for (int i = 1; i <= targetLength; i++) {
            char targetChar = target.charAt(i - 1);
            for (int j = 1; j <= sourceLength; j++) {
                char sourceChar = source.charAt(j - 1);
                double replace = targetChar == sourceChar ? 0 : weights[2];
                double best = Math.min(matrix[i - 1][j] + weights[1],
                        Math.min(matrix[i][j - 1] + weights[0], matrix[i - 1][j - 1] + replace));
                if (i > 1 && j > 1
                        && sourceChar == target.charAt(i - 2)
                        && targetChar == source.charAt(j - 2)) {
                    best = Math.min(best, matrix[i - 2][j - 2] + weights[3]);
                }
                matrix[i][j] = best;
            }
        }
        return matrix[targetLength][sourceLength];
    }

    private static String randomWord(Random random, String alphabet, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
