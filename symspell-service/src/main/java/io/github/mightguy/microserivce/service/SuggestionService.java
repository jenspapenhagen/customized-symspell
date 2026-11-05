package io.github.mightguy.microserivce.service;

import io.github.mightguy.microserivce.model.Suggestion;
import io.github.mightguy.microserivce.model.SuggestionResponse;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.DictionaryReader;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Loads a frequency dictionary once and answers spelling suggestions from it.
 *
 * <p>The dictionary is read from the classpath ({@code de-100k.txt}) and kept in an
 * {@link InMemoryDataHolder}. Lookups are read-only after startup and therefore thread safe.
 *
 * @author MighTguY
 * @since 6.7
 */
@Service
public final class SuggestionService {

    private static final String DICTIONARY_RESOURCE = "de-100k.txt";
    private static final int MAGIC_2 = 2;
    private static final int MAGIC_7 = 7;
    private static final int MAGIC_25 = 25;
    private static final int MAGIC_1000 = 1000;
    private static final int MAGIC_0 = 0;
    private static final String SPACE = " ";

    private static final Comparator<Suggestion> SUGGESTION_ORDER = (first, second) -> {
        final int byDistance = Double.compare(first.distance(), second.distance());
        if (byDistance != 0) {
            return byDistance;
        }
        final int byFrequency = Double.compare(second.frequency(), first.frequency());
        if (byFrequency != 0) {
            return byFrequency;
        }
        return first.term().compareTo(second.term());
    };

    private final InMemoryDataHolder dataHolder;
    private final SymSpellCheck spellCheck;
    private final int dictionarySize;

    /**
     * Creates the service and loads the bundled dictionary.
     *
     * @throws IOException if the dictionary resource cannot be read
     * @throws SpellCheckException if the dictionary cannot be indexed
     */
    public SuggestionService() throws IOException, SpellCheckException {
        final SpellCheckSettings settings = SpellCheckSettings.builder()
                .verbosity(Verbosity.ALL)
                .maxEditDistance(MAGIC_2)
                .prefixLength(MAGIC_7)
                .topK(MAGIC_25)
                .build();
        final WeightedDamerauLevenshteinDistance distance = new WeightedDamerauLevenshteinDistance(
                settings.getDeletionWeight(),
                settings.getInsertionWeight(),
                settings.getReplaceWeight(),
                settings.getTranspositionWeight(),
                null);
        this.dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        this.spellCheck = new SymSpellCheck(dataHolder, distance, settings);
        final List<DictionaryItem> items = loadDictionary();
        for (final DictionaryItem item : items) {
            dataHolder.addItem(item);
        }
        this.dictionarySize = items.size();
    }

    /**
     * Returns suggestions and a possible correction for the given query.
     *
     * @param query the raw user query, may be empty
     * @param limit the maximum number of suggestions, clamped to {@code 1..25}
     * @return the suggestion response, never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public SuggestionResponse suggest(final String query, final int limit) throws SpellCheckException {
        final String display = query == null ? "" : query.strip();
        final String normalized = normalize(display);
        final int effectiveLimit = Math.max(1, Math.min(MAGIC_25, limit));
        if (display.isEmpty()) {
            return new SuggestionResponse(display, false, null, List.of(), 0L, dictionarySize);
        }
        final long start = System.nanoTime();
        final List<Suggestion> suggestions = collect(normalized, effectiveLimit);
        Suggestion correction = firstCorrection(suggestions);
        boolean exact = isExact(normalized);
        if (normalized.contains(SPACE)) {
            final List<SuggestionItem> compound = spellCheck.lookupCompound(normalized, MAGIC_2, true);
            if (compound.isEmpty()) {
                return new SuggestionResponse(display, exact, correction, suggestions,
                        microsSince(start), dictionarySize);
            }
            correction = toSuggestion(compound.get(MAGIC_0));
            exact = correction.distance() == 0.0;
        }
        return new SuggestionResponse(display, exact, correction, suggestions,
                microsSince(start), dictionarySize);
    }

    /**
     * Returns the number of terms in the loaded dictionary.
     *
     * @return the dictionary size
     */
    public int getDictionarySize() {
        return dictionarySize;
    }

    private List<Suggestion> collect(final String query, final int limit) throws SpellCheckException {
        final List<SuggestionItem> items = spellCheck.lookup(query, Verbosity.ALL, MAGIC_2);
        final List<Suggestion> result = new ArrayList<>(items.size());
        for (final SuggestionItem item : items) {
            result.add(toSuggestion(item));
        }
        result.sort(SUGGESTION_ORDER);
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(MAGIC_0, limit));
        }
        return result;
    }

    private Suggestion firstCorrection(final List<Suggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return null;
        }
        final Suggestion best = suggestions.get(MAGIC_0);
        return best.distance() > 0.0 ? best : null;
    }

    private boolean isExact(final String query) throws SpellCheckException {
        return dataHolder.getItemFrequency(query) != null;
    }

    private Suggestion toSuggestion(final SuggestionItem item) {
        return new Suggestion(item.getTerm(), item.getDistance(), item.getCount());
    }

    private long microsSince(final long start) {
        return (System.nanoTime() - start) / MAGIC_1000;
    }

    private String normalize(final String value) {
        return value.toLowerCase(Locale.GERMAN);
    }

    private List<DictionaryItem> loadDictionary() throws IOException {
        final ClassLoader classLoader = SuggestionService.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(DICTIONARY_RESOURCE)) {
            if (input == null) {
                throw new IOException("Dictionary resource not found: " + DICTIONARY_RESOURCE);
            }
            return DictionaryReader.read(input);
        }
    }
}
