package io.github.mightguy.spellcheck.symspell.api;

import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import java.util.Map;

/**
 * Stores the dictionary, the derived delete index and the optional exclusion list used by a spell
 * checker.
 *
 * @author MighTguY
 * @since 6.7
 */
public interface DataHolder {

    /**
     * Adds a dictionary entry and indexes the deletes derived from it.
     *
     * @param dictionaryItem the term with its frequency, must not be {@code null}
     * @return {@code true} if the entry was added, {@code false} if it was filtered out
     * @throws SpellCheckException if the entry cannot be indexed
     */
    public boolean addItem(DictionaryItem dictionaryItem) throws SpellCheckException;

    /**
     * Returns the frequency of a unigram.
     *
     * @param term the term to look up
     * @return the frequency, or {@code null} if the term is unknown
     * @throws SpellCheckException if the lookup fails
     */
    public Double getItemFrequency(String term) throws SpellCheckException;

    /**
     * Returns the frequency of a bigram.
     *
     * @param term the bigram to look up, with its words separated by a space
     * @return the frequency, or {@code null} if the bigram is unknown
     * @throws SpellCheckException if the lookup fails
     */
    public Double getItemFrequencyBiGram(String term) throws SpellCheckException;

    /**
     * Returns the terms indexed under the delete of the given key.
     *
     * @param key the delete string
     * @return the matching terms, or {@code null} if the delete is unknown
     * @throws SpellCheckException if the lookup fails
     */
    public String[] getDeletes(String key) throws SpellCheckException;

    /**
     * Returns the ids of the terms indexed under the delete of the given key.
     *
     * @param key the delete string
     * @return the matching term ids, or {@code null} if unsupported or the delete is unknown
     * @throws SpellCheckException if the lookup fails
     */
    public default int[] getDeleteIds(String key) throws SpellCheckException {
        return null;
    }

    /**
     * Returns the largest unigram frequency known to this holder. Compound segmentation uses it to
     * normalise unigram costs.
     *
     * @return the largest frequency, or {@code 0.0} if the holder does not track one
     */
    public default double getMaxFrequency() {
        return 0.0;
    }

    /**
     * Returns the term stored for the given id.
     *
     * @param id the term id
     * @return the term, or {@code null} if the id is unknown
     */
    public default String getTerm(int id) {
        return null;
    }

    /**
     * Returns the frequency stored for the given term id.
     *
     * @param id the term id
     * @return the frequency, or {@code 0.0} if the id is unknown
     */
    public default double getTermFrequency(int id) {
        return 0.0;
    }

    /**
     * Returns the number of dictionary entries.
     *
     * @return the number of entries
     */
    public int getSize();

    /**
     * Removes all dictionary entries and derived indexes.
     *
     * @return {@code true} if the holder was cleared
     * @throws SpellCheckException if clearing fails
     */
    public boolean clear() throws SpellCheckException;

    /**
     * Adds a mapping from an unknown term to its correction.
     *
     * @param key the term to exclude from correction
     * @param value the replacement returned instead
     */
    public void addExclusionItem(String key, String value);

    /**
     * Adds several exclusion mappings.
     *
     * @param values the mappings to add
     */
    public void addExclusionItems(Map<String, String> values);

    /**
     * Returns the exclusion entry for the given term.
     *
     * @param key the term to look up
     * @return the configured replacement, or {@code null} if the term is not excluded
     */
    public String getExclusionItem(String key);
}
