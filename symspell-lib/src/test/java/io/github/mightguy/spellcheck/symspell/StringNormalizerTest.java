package io.github.mightguy.spellcheck.symspell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.StringNormalizer;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.util.List;
import org.junit.Test;

/**
 * Tests for the optional ASCII transliteration inspired by the Rust string strategies.
 */
public class StringNormalizerTest {

    @Test
    public void foldsGermanUmlautsAndSharpS() {
        assertEquals("Gruesse", StringNormalizer.toAscii("Grüße"));
        assertEquals("Oel", StringNormalizer.toAscii("Öl"));
        assertEquals("Aerger", StringNormalizer.toAscii("Ärger"));
    }

    @Test
    public void stripsRemainingDiacritics() {
        assertEquals("cafe", StringNormalizer.toAscii("café"));
        assertEquals("naive", StringNormalizer.toAscii("naïve"));
    }

    @Test
    public void leavesPlainAsciiUntouched() {
        final String term = "hello";
        assertTrue(term == StringNormalizer.toAscii(term));
    }

    @Test
    public void transliteratedLookupMatchesAsciiInput() throws Exception {
        final SpellCheckSettings settings = SpellCheckSettings.builder().transliterate(true).build();
        final InMemoryDataHolder holder =
                new InMemoryDataHolder(settings, new Utf16HashFunction());
        holder.addItem(new DictionaryItem("Grüße", 10.0, 0.0));

        final SymSpellCheck checker = new SymSpellCheck(holder,
                new WeightedDamerauLevenshteinDistance(1.0, 1.0, 1.0, 1.0, null),
                settings);

        final List<SuggestionItem> suggestions = checker.lookup("gruesse", Verbosity.TOP, 2);
        assertEquals(1, suggestions.size());
        assertEquals("gruesse", suggestions.get(0).getTerm());
    }
}
