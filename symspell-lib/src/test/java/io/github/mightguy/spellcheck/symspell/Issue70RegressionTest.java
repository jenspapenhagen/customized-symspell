
package io.github.mightguy.spellcheck.symspell;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.QwertyDistance;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression tests for https://github.com/MighTguY/customized-symspell/issues/70
 */
public class Issue70RegressionTest {

    private static final double EPSILON = 0.01;

    /**
     * Issue 70, point 4: isTransposition was off by one (i > 2 && j > 2 instead of i > 1 && j > 1),
     * so adjacent transpositions of the first two characters were reported as two edits.
     */
    @Test
    public void transpositionOfFirstTwoCharactersIsOneEdit() {
        WeightedDamerauLevenshteinDistance unweighted =
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null);
        WeightedDamerauLevenshteinDistance weighted =
                new WeightedDamerauLevenshteinDistance(0.8f, 1.01f, 0.9f, 0.7f, null);

        Assert.assertEquals(1, unweighted.getDistance("ab", "ba"), EPSILON);
        Assert.assertEquals(1, unweighted.getDistance("abc", "bac"), EPSILON);
        Assert.assertEquals(1, unweighted.getDistance("teh", "the"), EPSILON);

        Assert.assertEquals(0.7, weighted.getDistance("ab", "ba"), EPSILON);
        Assert.assertEquals(0.7, weighted.getDistance("abc", "bac"), EPSILON);
        Assert.assertEquals(0.7, weighted.getDistance("teh", "the"), EPSILON);
    }

    /**
     * Issue 70, point 3: getDistance must produce the same results with three rolling rows as with
     * the former full matrix - including asymmetric input lengths and a CharDistance implementation.
     */
    @Test
    public void rollingRowsMatchExpectedDistances() {
        WeightedDamerauLevenshteinDistance unweighted =
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null);
        WeightedDamerauLevenshteinDistance weighted =
                new WeightedDamerauLevenshteinDistance(0.8f, 1.01f, 0.9f, 0.7f, null);
        WeightedDamerauLevenshteinDistance withCharDistance =
                new WeightedDamerauLevenshteinDistance(0.8f, 1.01f, 0.9f, 0.7f, new QwertyDistance());

        Assert.assertEquals(1, unweighted.getDistance("sommer", "summer"), EPSILON);
        Assert.assertEquals(2, unweighted.getDistance("cool", "cola"), EPSILON);
        Assert.assertEquals(1, unweighted.getDistance("playrs", "players"), EPSILON);
        Assert.assertEquals(2, unweighted.getDistance("playrs", "player"), EPSILON);

        Assert.assertEquals(0.89, weighted.getDistance("sommer", "summer"), EPSILON);
        Assert.assertEquals(1.8, weighted.getDistance("cool", "cola"), EPSILON);
        Assert.assertEquals(0.7, weighted.getDistance("international", "internationla"), EPSILON);

        Assert.assertEquals(0.899, withCharDistance.getDistance("bigjest", "biggest"), EPSILON);
        Assert.assertEquals(1.25, withCharDistance.getDistance("cool", "cola"), EPSILON);

        Assert.assertEquals(0, weighted.getDistance("slime", "slime"), EPSILON);
        Assert.assertEquals(4, unweighted.getDistance("", "abcd"), EPSILON);
        Assert.assertEquals(4, unweighted.getDistance("abcd", ""), EPSILON);
        Assert.assertEquals(4, unweighted.getDistance("ab", "cdef"), EPSILON);
        Assert.assertEquals(4, unweighted.getDistance("cdef", "ab"), EPSILON);
        Assert.assertEquals(-1, weighted.getDistance("sommer", "summer", 0.5), EPSILON);
    }

    /**
     * Issue 70, point 1 + 2: candidates must be a FIFO queue (ArrayDeque.remove() instead of
     * ArrayList.remove(0)) and only the prefix xor the raw phrase may be queued. A phrase longer
     * than the prefix length must still be corrected through its prefix.
     */
    @Test
    public void lookupThroughPrefixStillFindsSuggestion() throws SpellCheckException {
        SpellCheckSettings settings = SpellCheckSettings.builder()
                .countThreshold(1)
                .deletionWeight(1)
                .insertionWeight(1)
                .replaceWeight(1)
                .maxEditDistance(2)
                .transpositionWeight(1)
                .topK(5)
                .prefixLength(4)
                .verbosity(Verbosity.TOP).build();

        WeightedDamerauLevenshteinDistance distance = new WeightedDamerauLevenshteinDistance(
                settings.getDeletionWeight(), settings.getInsertionWeight(), settings.getReplaceWeight(),
                settings.getTranspositionWeight(), null);
        DataHolder dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        SymSpellCheck spellChecker = new SymSpellCheck(dataHolder, distance, settings);

        dataHolder.addItem(new DictionaryItem("information", 1000.0, -1.0));
        dataHolder.addItem(new DictionaryItem("integration", 900.0, -1.0));
        dataHolder.addItem(new DictionaryItem("international", 800.0, -1.0));

        // phrase (10) longer than prefixLength (4): only the prefix is queued as candidate
        List<SuggestionItem> suggestions = spellChecker.lookup("informtion", Verbosity.TOP, 2);
        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("information", suggestions.get(0).getTerm());
        Assert.assertEquals(1.0, suggestions.get(0).getDistance(), EPSILON);

        // phrase shorter than or equal to prefixLength: the raw phrase itself is queued
        List<SuggestionItem> shortSuggestions = spellChecker.lookup("integraton", Verbosity.TOP, 2);
        Assert.assertFalse(shortSuggestions.isEmpty());
        Assert.assertEquals("integration", shortSuggestions.get(0).getTerm());
    }

    /**
     * Issue 70, point 2: lenDiff must be based on phrasePrefixLen (as in the original C#
     * implementation), otherwise candidates beyond maxEditDistance are pruned too early and valid
     * suggestions are lost when verbosity is ALL.
     */
    @Test
    public void lookupWithAllVerbosityKeepsSuggestions() throws SpellCheckException {
        SpellCheckSettings settings = SpellCheckSettings.builder()
                .countThreshold(1)
                .deletionWeight(1)
                .insertionWeight(1)
                .replaceWeight(1)
                .maxEditDistance(2)
                .transpositionWeight(1)
                .topK(5)
                .prefixLength(4)
                .verbosity(Verbosity.ALL).build();

        WeightedDamerauLevenshteinDistance distance = new WeightedDamerauLevenshteinDistance(
                settings.getDeletionWeight(), settings.getInsertionWeight(), settings.getReplaceWeight(),
                settings.getTranspositionWeight(), null);
        DataHolder dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        SymSpellCheck spellChecker = new SymSpellCheck(dataHolder, distance, settings);

        dataHolder.addItem(new DictionaryItem("players", 1000.0, -1.0));
        dataHolder.addItem(new DictionaryItem("player", 900.0, -1.0));

        List<SuggestionItem> suggestions = spellChecker.lookup("playrs", Verbosity.ALL, 2);
        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("players", suggestions.get(0).getTerm());
        Assert.assertEquals(1.0, suggestions.get(0).getDistance(), EPSILON);
    }

    /**
     * A budget of {@code Integer.MAX_VALUE} must not overflow the branch-and-bound check in the
     * bit-parallel kernel; compound lookups use that budget for their final distance.
     */
    @Test
    public void largeBudgetIsNotRejected() {
        WeightedDamerauLevenshteinDistance distance =
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null);

        Assert.assertEquals(1.0, distance.getDistance("haus boot", "hausboot",
                Math.pow(2, 31) - 1), EPSILON);
        Assert.assertEquals(1.0, distance.getDistance("haus boot", "hausboot"), EPSILON);
    }
}
