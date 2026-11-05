
package io.github.mightguy.spellcheck.symspell.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Stateless helpers shared by the spell checker implementations.
 *
 * @author MighTguY
 * @since 6.7
 */
public final class SpellHelper {
    private static final int DELETE_BUFFER_INITIAL_CAPACITY = 64;


    /**
     * Scratch buffer for delete generation. Deletes are built in place and turned into a
     * {@link String} right away, so a single buffer per thread is enough even while recursing.
     */
    private static final ThreadLocal<char[]> DELETE_BUFFER = ThreadLocal.withInitial(
            () -> new char[DELETE_BUFFER_INITIAL_CAPACITY]);

    private SpellHelper() {
    }

    /**
     * Generates the deletes of the given key down to the configured edit distance.
     *
     * <p>The key is truncated to {@code prefixLength} first, because deletes beyond the prefix can
     * never match a lookup candidate.
     *
     * @param theKey the term to generate deletes for
     * @param maxEditDistance the maximum number of deletes
     * @param prefixLength the maximum prefix length used for deletes
     * @param editFactor factor applied to the term length to derive the edit distance
     * @return the generated deletes, including the (possibly truncated) key itself
     */
    public static Set<String> getEditDeletes(final String theKey,
            final double maxEditDistance, final int prefixLength, final double editFactor) {
        String key = theKey;
        final Set<String> deletedWords = new HashSet<>();
        if (key.length() <= maxEditDistance) {
            deletedWords.add("");
        }
        if (key.length() > prefixLength) {
            key = key.substring(0, prefixLength);
        }
        deletedWords.add(key);
        return edits(key, 0, deletedWords, getEdistance(maxEditDistance, key.length(), editFactor));
    }

    private static Double getEdistance(final double maxEditDistance, final int length, final double factor) {
        final double computedEd = Math.round(factor * length);
        if (Math.min(maxEditDistance, computedEd) == maxEditDistance) {
            return maxEditDistance;
        }
        return computedEd;
    }

    /**
     * Generates the deletes of a word for the symmetric delete index. Only deletions are produced;
     * transpositions, replacements and insertions are expensive and language dependent.
     *
     * @param word the word to derive deletes from
     * @param theEditDistance the maximum number of deletions
     * @param deletedWords the set that receives the deletes
     * @param maxEd an optional hard limit for the edit distance, may be {@code null}
     * @return the same set instance that was passed in, containing all deletes
     */
    public static Set<String> edits(final String word, final double theEditDistance, final Set<String> deletedWords,
            final Double maxEd) {
        double editDistance = theEditDistance;
        editDistance++;
        final int wordLength = word.length();
        if (wordLength < 1) {
            return deletedWords;
        }

        char[] buffer = DELETE_BUFFER.get();
        if (buffer.length < wordLength - 1) {
            buffer = new char[wordLength - 1];
            DELETE_BUFFER.set(buffer);
        }

        for (int i = 0; i < wordLength; i++) {
            word.getChars(0, i, buffer, 0);
            word.getChars(i + 1, wordLength, buffer, i);
            final String delete = new String(buffer, 0, wordLength - 1);
            if (deletedWords.add(delete) && editDistance < maxEd) {
                edits(delete, editDistance, deletedWords, maxEd);
            }
        }
        return deletedWords;
    }

    /**
     * Adds a placeholder suggestion when the lookup produced no results and unknown words are not
     * ignored.
     *
     * @param suggestionItems the suggestion list to fill
     * @param phrase the original input
     * @param maxEditDistance the maximum edit distance
     * @param ignoreUnknown whether unknown words are ignored
     * @return the same list instance that was passed in
     */
    public static List<SuggestionItem> earlyExit(final List<SuggestionItem> suggestionItems,
            final String phrase, final double maxEditDistance, final boolean ignoreUnknown) {
        if ((suggestionItems == null || suggestionItems.isEmpty()) && !ignoreUnknown) {
            suggestionItems.add(new SuggestionItem(phrase, maxEditDistance + 1, 0));
        }
        return suggestionItems;
    }

    /**
     * Splits the given text on whitespace.
     *
     * @param word the text to split
     * @return the words; empty strings are not produced for well-formed input
     */
    public static String[] tokenizeOnWhiteSpace(final String word) {
        return word.split("\\s+");
    }

    /**
     * Compares two doubles, treating values closer than the threshold as equal.
     *
     * @param d1 the first value
     * @param d2 the second value
     * @param threshold the equality tolerance, must be positive
     * @return {@code true} if {@code d1} is approximately equal to or less than {@code d2}
     */
    public static boolean isLessOrEqualDouble(final double d1, final double d2, final double threshold) {
        return Math.abs(d1 - d2) < threshold || d1 < d2;
    }

    /**
     * Compares two doubles, treating values closer than the threshold as equal.
     *
     * @param d1 the first value
     * @param d2 the second value
     * @param threshold the equality tolerance, must be positive
     * @return {@code true} if {@code d1} is strictly less than {@code d2}
     */
    public static boolean isLessDouble(final double d1, final double d2, final double threshold) {
        return !isEqualDouble(d1, d2, threshold) && d1 < d2;
    }


    /**
     * Checks whether two doubles are equal within the given tolerance.
     *
     * @param d1 the first value
     * @param d2 the second value
     * @param threshold the equality tolerance, must be positive
     * @return {@code true} if the values differ by less than the threshold
     */
    public static boolean isEqualDouble(final double d1, final double d2, final double threshold) {
        return Math.abs(d1 - d2) < threshold;
    }

    /**
     * Checks whether the best suggestion of a partial lookup equals the best suggestion of the
     * complete word, in which case the split can be skipped.
     *
     * @param suggestions the suggestions for the complete word, may be {@code null}
     * @param suggestions1 the suggestions for the first part
     * @return {@code true} if the heads are the same
     */
    public static boolean continueConditionIfHeadIsSame(final List<SuggestionItem> suggestions,
            final List<SuggestionItem> suggestions1) {
        return (suggestions1.isEmpty())
                || ((suggestions != null && !suggestions.isEmpty())
                && !suggestions1.isEmpty()
                && suggestions.get(0).equals(suggestions1.get(0)));
    }

}
