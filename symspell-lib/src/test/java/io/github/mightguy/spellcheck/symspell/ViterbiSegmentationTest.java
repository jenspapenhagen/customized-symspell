package io.github.mightguy.spellcheck.symspell;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the opt-in Viterbi style compound segmentation.
 */
public class ViterbiSegmentationTest {

    private static SymSpellCheck createSpellCheck(final boolean viterbi, final Object[][] words)
            throws Exception {
        final SpellCheckSettings settings = SpellCheckSettings.builder()
                .prefixLength(5)
                .maxEditDistance(2)
                .viterbiSegmentation(viterbi)
                .build();
        final DataHolder dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        for (Object[] word : words) {
            dataHolder.addItem(new DictionaryItem((String) word[0], (Double) word[1], -1.0));
        }
        return new SymSpellCheck(dataHolder,
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null), settings);
    }

    private static final Object[][] COMPOUND_WORDS = {
        {"haus", 5_149_656.0},
        {"boot", 455_005.0},
        {"benutzer", 657_083.0},
        {"ober", 1_402_841.0},
        {"flaeche", 2_382_862.0}
    };

    /**
     * The setting must default to disabled and round-trip through the builder.
     */
    @Test
    public void settingRoundTrip() {
        Assert.assertFalse(SpellCheckSettings.builder().build().isViterbiSegmentation());
        Assert.assertTrue(SpellCheckSettings.builder()
                .viterbiSegmentation(true)
                .build()
                .isViterbiSegmentation());
    }

    /**
     * A joined word must be segmented into all of its known parts.
     *
     * @throws Exception if the lookup fails
     */
    @Test
    public void segmentsEveryPart() throws Exception {
        final SymSpellCheck spellCheck = createSpellCheck(true, COMPOUND_WORDS);

        final List<SuggestionItem> suggestions = spellCheck.lookupCompound("hausboot", 2, false);

        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("haus boot", suggestions.get(0).getTerm());
    }

    /**
     * The dynamic program must cover the complete input instead of dropping middle characters.
     *
     * @throws Exception if the lookup fails
     */
    @Test
    public void keepsMiddleParts() throws Exception {
        final SymSpellCheck spellCheck = createSpellCheck(true, COMPOUND_WORDS);

        final List<SuggestionItem> suggestions =
                spellCheck.lookupCompound("benutzeroberflaeche", 2, false);

        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("benutzer ober flaeche", suggestions.get(0).getTerm());
    }

    /**
     * When the setting is disabled the legacy single split heuristic stays in charge.
     *
     * @throws Exception if the lookup fails
     */
    @Test
    public void disabledSettingKeepsLegacySplit() throws Exception {
        final SymSpellCheck spellCheck = createSpellCheck(false, COMPOUND_WORDS);

        final List<SuggestionItem> suggestions =
                spellCheck.lookupCompound("benutzeroberflaeche", 2, false);

        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("benutzer flaeche", suggestions.get(0).getTerm());
    }

    /**
     * Unknown input must fall back instead of producing an arbitrary segmentation.
     *
     * @throws Exception if the lookup fails
     */
    @Test
    public void unknownWordFallsBack() throws Exception {
        final SymSpellCheck spellCheck = createSpellCheck(true, COMPOUND_WORDS);

        final List<SuggestionItem> suggestions = spellCheck.lookupCompound("qwxzptr", 2, false);

        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("qwxzptr", suggestions.get(0).getTerm());
    }
}
