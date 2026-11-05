package io.github.mightguy.spellcheck.symspell.api;

import io.github.mightguy.spellcheck.symspell.common.Composition;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import java.util.List;

/**
 * Base class for spell checkers built on the Symmetric Delete algorithm.
 *
 * <p>Subclasses provide the concrete delete index and the edit distance calculation. The overloads
 * in this class resolve the remaining arguments from the configured {@link SpellCheckSettings}.
 *
 * @author MighTguY
 * @since 6.7
 */
public abstract class SpellChecker {

    /**
     * The dictionary holder that stores the terms and their delete index.
     */
    protected DataHolder dataHolder;

    /**
     * The metric used to verify a candidate and to report the final edit distance.
     */
    protected StringDistance stringDistance;

    /**
     * The settings that control verbosity, weights and lookup limits.
     */
    protected SpellCheckSettings spellCheckSettings;

    /**
     * Creates a spell checker with the given dictionary, distance metric and settings.
     *
     * @param theDataHolder the dictionary holder, must not be {@code null}
     * @param theStringDistance the distance metric used for the final distance calculation, must not
     *     be {@code null}
     * @param theSpellCheckSettings the settings of this spell checker, must not be {@code null}
     */
    public SpellChecker(final DataHolder theDataHolder, final StringDistance theStringDistance,
            final SpellCheckSettings theSpellCheckSettings) {
        this.dataHolder = theDataHolder;
        this.stringDistance = theStringDistance;
        this.spellCheckSettings = theSpellCheckSettings;
    }

    /**
     * Returns the dictionary holder backing this spell checker.
     *
     * @return the dictionary holder, never {@code null}
     */
    public DataHolder getDataHolder() {
        return dataHolder;
    }

    /**
     * Returns the distance metric used for the final distance calculation.
     *
     * @return the distance metric, never {@code null}
     */
    public StringDistance getStringDistance() {
        return stringDistance;
    }

    /**
     * Returns the settings used by this spell checker.
     *
     * @return the settings, never {@code null}
     */
    public SpellCheckSettings getSpellCheckSettings() {
        return spellCheckSettings;
    }

    /**
     * Looks up suggestions using the configured verbosity and maximum edit distance.
     *
     * @param word the word to correct
     * @return suggestions ordered by distance, then frequency; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public List<SuggestionItem> lookup(final String word) throws SpellCheckException {
        return lookup(word, spellCheckSettings.getVerbosity(), spellCheckSettings.getMaxEditDistance());
    }

    /**
     * Looks up suggestions using the given verbosity and the configured maximum edit distance.
     *
     * @param word the word to correct
     * @param verbosity controls how many suggestions are returned
     * @return suggestions ordered by distance, then frequency; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public List<SuggestionItem> lookup(final String word, final Verbosity verbosity) throws SpellCheckException {
        return lookup(word, verbosity, spellCheckSettings.getMaxEditDistance());
    }

    /**
     * Looks up suggestions for a single word.
     *
     * @param word the word to correct
     * @param verbosity controls how many suggestions are returned
     * @param editDistance the maximum edit distance, must be non-negative
     * @return suggestions ordered by distance, then frequency; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public abstract List<SuggestionItem> lookup(String word, Verbosity verbosity, double editDistance)
            throws SpellCheckException;

    /**
     * Corrects a possibly multi-word input using the configured maximum edit distance and splitting.
     *
     * @param word the input to correct
     * @return the corrected terms, possibly with inserted spaces; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public List<SuggestionItem> lookupCompound(final String word) throws SpellCheckException {
        return lookupCompound(word, spellCheckSettings.getMaxEditDistance());
    }

    /**
     * Corrects a possibly multi-word input, optionally tokenizing on whitespace.
     *
     * @param word the input to correct
     * @param editDistance the maximum edit distance, must be non-negative
     * @param tokenizeOnWhiteSpace whether the input is split into words before correction
     * @return the corrected terms; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public abstract List<SuggestionItem> lookupCompound(String word, double editDistance,
            boolean tokenizeOnWhiteSpace)
            throws SpellCheckException;

    /**
     * Corrects a possibly multi-word input with whitespace tokenization enabled.
     *
     * @param word the input to correct
     * @param editDistance the maximum edit distance, must be non-negative
     * @return the corrected terms; never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    public List<SuggestionItem> lookupCompound(final String word, final double editDistance)
            throws SpellCheckException {
        return lookupCompound(word, editDistance, true);
    }

    /**
     * Segments and corrects the given phrase using the configured prefix length and edit distance.
     *
     * @param phrase the input phrase
     * @return the segmentation result including the corrected string and distance sum
     * @throws SpellCheckException if the segmentation fails
     */
    public Composition wordBreakSegmentation(final String phrase) throws SpellCheckException {
        return wordBreakSegmentation(phrase, spellCheckSettings.getPrefixLength(),
                spellCheckSettings.getMaxEditDistance());
    }

    /**
     * Segments and corrects the given phrase using the configured prefix length and the given edit
     * distance.
     *
     * @param phrase the input phrase
     * @param ed the maximum edit distance, must be non-negative
     * @return the segmentation result including the corrected string and distance sum
     * @throws SpellCheckException if the segmentation fails
     */
    public Composition wordBreakSegmentation(final String phrase, final Double ed) throws SpellCheckException {
        return wordBreakSegmentation(phrase, spellCheckSettings.getPrefixLength(),
                ed);
    }

    /**
     * Segments and corrects the given phrase.
     *
     * @param phrase the input phrase
     * @param maxSegmentationWordLength the maximum length of a segmented word, must be positive
     * @param maxEditDistance the maximum edit distance, must be non-negative
     * @return the segmentation result including the corrected string and distance sum
     * @throws SpellCheckException if the segmentation fails
     */
    public abstract Composition wordBreakSegmentation(String phrase, int maxSegmentationWordLength,
            double maxEditDistance) throws SpellCheckException;

}
