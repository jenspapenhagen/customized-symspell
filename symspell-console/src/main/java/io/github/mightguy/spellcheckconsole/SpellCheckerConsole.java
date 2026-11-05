package io.github.mightguy.spellcheckconsole;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.common.Composition;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command line front end that loads a dictionary and runs lookups or compound correction.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellCheckerConsole {

    /**
     * Creates a console front end. The dictionary is loaded by {@link #init()}.
     */
    public SpellCheckerConsole() {
        // dictionary is loaded in init()
    }

    private static final Logger log = LoggerFactory.getLogger(SpellCheckerConsole.class);
    private static final int MAX_SUGGESTIONS = 10;

    private DataHolder dataHolder;
    private SymSpellCheck symSpellCheck;


    private boolean init() throws IOException, SpellCheckException {
        final ClassLoader classLoader = SpellCheckerConsole.class.getClassLoader();

        final SpellCheckSettings spellCheckSettings = SpellCheckSettings.builder().build();

        final WeightedDamerauLevenshteinDistance weightedDamerauLevenshteinDistance =
                new WeightedDamerauLevenshteinDistance(spellCheckSettings.getDeletionWeight(),
                        spellCheckSettings.getInsertionWeight(), spellCheckSettings.getReplaceWeight(),
                        spellCheckSettings.getTranspositionWeight(), null);
        dataHolder = new InMemoryDataHolder(spellCheckSettings, new Utf16HashFunction());

        symSpellCheck = new SymSpellCheck(dataHolder, weightedDamerauLevenshteinDistance,
                spellCheckSettings);
        loadUniGramFile(
                new File(classLoader.getResource("frequency_dictionary_en_82_765.txt").getFile()));
        loadBiGramFile(
                new File(classLoader.getResource("frequency_bigramdictionary_en_243_342.txt").getFile()));

        return false;
    }

    private void loadUniGramFile(final File file) throws IOException, SpellCheckException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] arr = line.split("\\s+");
                dataHolder.addItem(new DictionaryItem(arr[0], Double.parseDouble(arr[1]), -1.0));
            }
        }
    }

    private void loadBiGramFile(final File file) throws IOException, SpellCheckException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] arr = line.split("\\s+");
                dataHolder
                        .addItem(new DictionaryItem(arr[0] + " " + arr[1], Double.parseDouble(arr[2]), -1.0));
            }
        }
    }

    private void suggestItem(final String[] args) throws IOException, SpellCheckException {
        if (args.length > 0) {
            suggestItemOnArgs(args[0]);
        } else {
            suggestItemOnConsole();
        }

    }

    private void suggestItemOnArgs(final String inputTerm) throws SpellCheckException {
        final List<SuggestionItem> suggestions = symSpellCheck.lookup(inputTerm);
        final SuggestionItem compound = symSpellCheck.lookupCompound(inputTerm).get(0);
        final Composition composition = symSpellCheck.wordBreakSegmentation(inputTerm, 10, 2);

        suggestions.stream()
                .limit(MAX_SUGGESTIONS)
                .forEach(suggestion -> log.info(
                        "Lookup suggestion: "
                                + suggestion.getTerm() + " "
                                + suggestion.getDistance() + " "
                                + suggestion.getCount()));
        log.info("LookupCompound: " + compound.getTerm());
        log.info("Composition: " + composition.getCorrectedString());
    }

    private void suggestItemOnConsole() throws IOException, SpellCheckException {
        String inputTerm;
        final BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            log.info("Please enter the term to get the suggest Item");
            inputTerm = reader.readLine();
            if (inputTerm == null || inputTerm.equalsIgnoreCase("q")) {
                return;
            }
            suggestItemOnArgs(inputTerm);
        }
    }

    /**
     * Runs the console front end.
     *
     * @param args the command line arguments: dictionary path and optional mode
     * @throws IOException if a dictionary file cannot be read
     * @throws SpellCheckException if the dictionary cannot be indexed
     */
    public static void main(final String[] args) throws IOException, SpellCheckException {
        final SpellCheckerConsole spellCheckerConsole = new SpellCheckerConsole();
        spellCheckerConsole.init();
        spellCheckerConsole.suggestItem(args);
    }
}
