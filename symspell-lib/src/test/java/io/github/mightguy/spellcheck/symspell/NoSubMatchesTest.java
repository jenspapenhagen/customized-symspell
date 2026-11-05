package io.github.mightguy.spellcheck.symspell;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the opt-in sub-match guard used by compound splitting.
 */
public class NoSubMatchesTest {

    /**
     * The builder flag must round-trip and default to disabled.
     */
    @Test
    public void settingRoundTrip() {
        Assert.assertFalse(SpellCheckSettings.builder().build().isNoSubMatches());
        Assert.assertTrue(SpellCheckSettings.builder().noSubMatches(true).build().isNoSubMatches());
    }

    /**
     * A split into two exact dictionary terms must still be accepted when the guard is enabled.
     *
     * @throws Exception if the lookup fails
     */
    @Test
    public void exactSplitStillAccepted() throws Exception {
        final SpellCheckSettings settings = SpellCheckSettings.builder()
                .prefixLength(5)
                .maxEditDistance(2)
                .noSubMatches(true)
                .build();
        final DataHolder dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        dataHolder.addItem(new DictionaryItem("haus", 100.0, -1.0));
        dataHolder.addItem(new DictionaryItem("boot", 90.0, -1.0));
        final SymSpellCheck symSpellCheck = new SymSpellCheck(dataHolder,
                new WeightedDamerauLevenshteinDistance(1, 1, 1, 1, null), settings);

        final List<SuggestionItem> suggestions = symSpellCheck.lookupCompound("hausboot", 2, false);
        Assert.assertFalse(suggestions.isEmpty());
        Assert.assertEquals("haus boot", suggestions.get(0).getTerm());
    }
}