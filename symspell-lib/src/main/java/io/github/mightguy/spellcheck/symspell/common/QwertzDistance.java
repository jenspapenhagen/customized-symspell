package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.CharDistance;
import java.util.HashMap;
import java.util.Map;

/**
 * Qwertz distance between two chars, using the German keyboard layout.
 *
 * <p>Directly adjacent keys cost {@code 0.1}, diagonal neighbours {@code 0.4} and unrelated
 * characters the default cost {@code 1.0}.
 *
 * @author MighTguY
 * @since 6.7
 */
public class QwertzDistance implements CharDistance {
    private static final double MAGIC_0_1f = 0.1f;
    private static final double MAGIC_0_4f = 0.4f;


    double directConnect = MAGIC_0_1f;
    double diagonalConnect = MAGIC_0_4f;
    double defaultValue = 1f;

    Map<String, Double> operationCost = new HashMap<>();

    /**
     * Creates the distance and initializes the keyboard adjacency matrix.
     */
    public QwertzDistance() {
        this.initializeCostMatrix();
    }

    @Override
    public double distance(final char a, final char b) {
        if (a == b) {
            return 0;
        }
        return operationCost
                .getOrDefault(a + "-" + b, operationCost.getOrDefault(b + "-" + a, defaultValue));
    }

    /**
     * Initializing the cost matrix.
     */
    public void initializeCostMatrix() {

        //Middle row
        addReplaceWeight('a', "s", directConnect);
        addReplaceWeight('a', "qwy", diagonalConnect);

        addReplaceWeight('s', "ad", directConnect);
        addReplaceWeight('s', "wexy", diagonalConnect);

        addReplaceWeight('d', "sf", directConnect);
        addReplaceWeight('d', "erxc", diagonalConnect);

        addReplaceWeight('f', "sf", directConnect);
        addReplaceWeight('f', "erxc", diagonalConnect);

        addReplaceWeight('g', "fh", directConnect);
        addReplaceWeight('g', "tzvb", diagonalConnect);

        addReplaceWeight('h', "gj", directConnect);
        addReplaceWeight('h', "zubn", diagonalConnect);

        addReplaceWeight('j', "hk", directConnect);
        addReplaceWeight('j', "uinm", diagonalConnect);

        addReplaceWeight('k', "jl", directConnect);
        addReplaceWeight('k', "iom", diagonalConnect);

        addReplaceWeight('l', "kö", directConnect);
        addReplaceWeight('l', "op", diagonalConnect);

        addReplaceWeight('ö', "lä", directConnect);
        addReplaceWeight('ö', "pü", diagonalConnect);

        addReplaceWeight('ä', "ö", directConnect);
        addReplaceWeight('ä', "ü", diagonalConnect);

        //Top Row

        addReplaceWeight('q', "w", directConnect);
        addReplaceWeight('q', "a", diagonalConnect);

        addReplaceWeight('w', "qe", directConnect);
        addReplaceWeight('w', "as", diagonalConnect);

        addReplaceWeight('e', "wr", directConnect);
        addReplaceWeight('e', "sd", diagonalConnect);

        addReplaceWeight('r', "et", directConnect);
        addReplaceWeight('r', "df", diagonalConnect);

        addReplaceWeight('t', "rz", directConnect);
        addReplaceWeight('t', "fg", diagonalConnect);

        addReplaceWeight('z', "tu", directConnect);
        addReplaceWeight('z', "gh", diagonalConnect);

        addReplaceWeight('u', "zi", directConnect);
        addReplaceWeight('u', "hj", diagonalConnect);

        addReplaceWeight('i', "uo", directConnect);
        addReplaceWeight('i', "jk", diagonalConnect);

        addReplaceWeight('o', "ip", directConnect);
        addReplaceWeight('o', "kl", diagonalConnect);

        addReplaceWeight('p', "oü", directConnect);
        addReplaceWeight('p', "lö", diagonalConnect);

        addReplaceWeight('ü', "p", directConnect);
        addReplaceWeight('ü', "öä", diagonalConnect);

        //Bottom Row

        addReplaceWeight('y', "x", directConnect);
        addReplaceWeight('y', "sa", diagonalConnect);

        addReplaceWeight('x', "yc", directConnect);
        addReplaceWeight('x', "sd", diagonalConnect);

        addReplaceWeight('c', "xv", directConnect);
        addReplaceWeight('c', "df", diagonalConnect);

        addReplaceWeight('v', "bc", directConnect);
        addReplaceWeight('v', "fg", diagonalConnect);

        addReplaceWeight('b', "vn", directConnect);
        addReplaceWeight('b', "gh", diagonalConnect);

        addReplaceWeight('n', "bm", directConnect);
        addReplaceWeight('n', "hj", diagonalConnect);

        addReplaceWeight('m', "n", directConnect);
        addReplaceWeight('m', "jk", diagonalConnect);

    }

    /**
     *
     * @param a
     * @param listOfChars
     * @param connectWeight
     */
    private void addReplaceWeight(final char a, final String listOfChars, final double connectWeight) {
        for (char ch : listOfChars.toCharArray()) {
            operationCost.put(a + "-" + ch, connectWeight);
        }
    }

    /**
     * Overrides the replacement cost for a specific key pair.
     *
     * @param a the first character, must be a lower-case letter
     * @param b the second character, must be a lower-case letter
     * @param connectWeight the cost to use, must be between {@code 0.0} and {@code 1.0}
     */
    public void addReplaceWeight(final char a, final char b, final double connectWeight) {
        operationCost.put(a + "-" + b, connectWeight);
    }
}
