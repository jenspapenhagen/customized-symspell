package io.github.mightguy.spellcheck.symspell.impl;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.api.HashFunction;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SpellHelper;
import io.github.mightguy.spellcheck.symspell.common.StringNormalizer;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link DataHolder} backed by compact term ids and a hash-indexed delete map.
 *
 * <p>Terms and frequencies live in parallel arrays and are addressed by an {@code int} id; delete
 * buckets store only these ids. This keeps the dictionary compact and lets lookups avoid string
 * copies on the hot path.
 *
 * @author MighTguY
 * @since 6.7
 */
public class InMemoryDataHolder implements DataHolder {
    private static final int MAGIC_1024 = 1024;


    private StringIntMap termIndex = new StringIntMap();
    private String[] terms = new String[MAGIC_1024];
    private double[] frequencies = new double[MAGIC_1024];
    private int termCount;

    private Map<String, Double> bigramsDictionary = new HashMap<>();
    private Map<String, String> exclusionDictionary = new HashMap<>();
    private Map<String, Double> belowThresholdWords = new HashMap<>();

    private final LongIntMap deleteIndex = new LongIntMap();
    private int[][] deleteBuckets = new int[MAGIC_1024][];
    private int deleteBucketCount;

    private SpellCheckSettings spellCheckSettings;
    private Utf16HashFunction hashFunction;

    /**
     * Creates a holder with the given settings and hash function.
     *
     * @param theSpellCheckSettings the settings controlling thresholds and lengths, must not be
     *     {@code null}
     * @param theHashFunction the hash function used for the delete index, must not be {@code null}
     */
    public InMemoryDataHolder(
            final SpellCheckSettings theSpellCheckSettings,
            final Utf16HashFunction theHashFunction) {
        this.spellCheckSettings = theSpellCheckSettings;
        this.hashFunction = theHashFunction;
    }

    @Override
    public boolean addItem(final DictionaryItem dictionaryItem) throws SpellCheckException {

        if (dictionaryItem.frequency() <= 0 && spellCheckSettings.getCountThreshold() > 0) {
            return false;
        }

        double frequency = dictionaryItem.frequency();
        String key = dictionaryItem.term();
        if (spellCheckSettings.isTransliterate()) {
            key = StringNormalizer.toAscii(key);
        }
        if (spellCheckSettings.isLowerCaseTerms()) {
            key = key.toLowerCase();
        }
        if (frequency <= 0) {
            frequency = 0;
        }

        frequency = addItemToBelowThreshold(key, frequency);

        if (Double.compare(frequency, Double.MIN_VALUE) == 0) {
            return false;
        }

        if (!addToDictionary(key, frequency)) {
            return false;
        }

        if (key.length() > spellCheckSettings.getMaxLength()) {
            spellCheckSettings.setMaxLength(key.length());
        }

        final int termId = termIndex.get(key);

        final Set<String> editDeletes = SpellHelper
                .getEditDeletes(key, spellCheckSettings.getMaxEditDistance(),
                        spellCheckSettings.getPrefixLength(), spellCheckSettings.getEditFactor());
        for (String delete : editDeletes) {
            final long hash = hashFunction.hash(delete);
            int bucket = deleteIndex.get(hash);
            if (bucket < 0) {
                bucket = deleteBucketCount++;
                if (bucket == deleteBuckets.length) {
                    deleteBuckets = Arrays.copyOf(deleteBuckets, bucket * 2);
                }
                deleteBuckets[bucket] = new int[]{termId};
                deleteIndex.put(hash, bucket);
            } else {
                final int[] suggestions = deleteBuckets[bucket];
                final int[] newSuggestions = Arrays.copyOf(suggestions, suggestions.length + 1);
                final double newFrequency = frequencies[termId];
                int insertPosition = suggestions.length;
                while (insertPosition > 0
                        && frequencies[newSuggestions[insertPosition - 1]] < newFrequency) {
                    newSuggestions[insertPosition] = newSuggestions[insertPosition - 1];
                    insertPosition--;
                }
                newSuggestions[insertPosition] = termId;
                deleteBuckets[bucket] = newSuggestions;
            }
        }
        return true;
    }

    private boolean addToDictionary(final String key, final double frequency) {
        if (spellCheckSettings.isDoKeySplit()
                && key.split(spellCheckSettings.getKeySplitRegex()).length > 1) {
            bigramsDictionary.put(key, frequency);
            if (frequency < spellCheckSettings.getBigramCountMin()) {
                spellCheckSettings.setBigramCountMin(frequency);
            }
            return false;
        } else {
            final int id = termIndex.get(key);
            if (id < 0) {
                if (termCount == terms.length) {
                    terms = Arrays.copyOf(terms, termCount * 2);
                    frequencies = Arrays.copyOf(frequencies, termCount * 2);
                }
                terms[termCount] = key;
                frequencies[termCount] = frequency;
                termIndex.put(key, termCount);
                termCount++;
            } else {
                frequencies[id] = frequency;
            }
            return true;
        }
    }

    @Override
    public Double getItemFrequency(final String term) throws SpellCheckException {
        final int id = termIndex.get(term);
        return id < 0 ? null : frequencies[id];
    }

    @Override
    public Double getItemFrequencyBiGram(final String term) throws SpellCheckException {
        return bigramsDictionary.getOrDefault(term, null);
    }

    @Override
    public String[] getDeletes(final String key) {
        final int[] ids = getDeleteIds(key);
        if (ids == null) {
            return null;
        }
        final String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = terms[ids[i]];
        }
        return out;
    }

    @Override
    public int[] getDeleteIds(final String key) {
        final int bucket = deleteIndex.get(hashFunction.hash(key));
        return bucket < 0 ? null : deleteBuckets[bucket];
    }

    /**
     * Returns the hash function used for the delete index.
     *
     * @return the hash function, never {@code null}
     */
    public Utf16HashFunction getHashFunction() {
        return hashFunction;
    }

    /**
     * Returns the delete index mapping a delete hash to its bucket.
     *
     * @return the delete index, never {@code null}
     */
    public LongIntMap getDeleteIndex() {
        return deleteIndex;
    }

    /**
     * Returns the delete buckets addressed by the delete index.
     *
     * @return the bucket array; entries may be {@code null}
     */
    public int[][] getDeleteBuckets() {
        return deleteBuckets;
    }

    @Override
    public String getTerm(final int id) {
        if (id < 0 || id >= termCount) {
            return null;
        }
        return terms[id];
    }

    @Override
    public double getTermFrequency(final int id) {
        if (id < 0 || id >= termCount) {
            return 0.0;
        }
        return frequencies[id];
    }

    /**
     * Returns the backing term array. Only the first {@link #getSize()} entries are valid.
     *
     * @return the term array
     */
    public String[] getTerms() {
        return terms;
    }

    /**
     * Returns the backing frequency array, parallel to {@link #getTerms()}.
     *
     * @return the frequency array
     */
    public double[] getFrequencies() {
        return frequencies;
    }

    @Override
    public int getSize() {
        return termCount;
    }

    @Override
    public boolean clear() {
        termIndex.clear();
        terms = new String[MAGIC_1024];
        frequencies = new double[MAGIC_1024];
        termCount = 0;
        deleteIndex.clear();
        deleteBuckets = new int[MAGIC_1024][];
        deleteBucketCount = 0;
        belowThresholdWords.clear();
        bigramsDictionary.clear();
        exclusionDictionary.clear();
        return false;
    }

    private double addItemToBelowThreshold(final String key, final double theFrequency) {
        double frequency = theFrequency;
        if (spellCheckSettings.getCountThreshold() > 1 && belowThresholdWords.containsKey(key)) {
            final double prevFreq = belowThresholdWords.get(key);
            frequency =
                    prevFreq + (Double.MAX_VALUE - prevFreq > frequency ? frequency : Double.MAX_VALUE);
            if (frequency > spellCheckSettings.getCountThreshold()) {
                belowThresholdWords.remove(key);
            } else {
                belowThresholdWords.put(key, frequency);
                return Double.MIN_VALUE;
            }
        } else if (termIndex.get(key) >= 0) {
            final double prevFreq = frequencies[termIndex.get(key)];
            frequency =
                    prevFreq + (Double.MAX_VALUE - prevFreq > frequency ? frequency : Double.MAX_VALUE);
            addToDictionary(key, frequency);
            return Double.MIN_VALUE;
        } else if (frequency < spellCheckSettings.getCountThreshold()) {
            belowThresholdWords.put(key, frequency);
            return Double.MIN_VALUE;
        }
        return frequency;
    }

    @Override
    public void addExclusionItem(final String key, final String value) {
        exclusionDictionary.put(key, value);
    }

    @Override
    public void addExclusionItems(final Map<String, String> values) {
        exclusionDictionary.putAll(values);
    }

    @Override
    public String getExclusionItem(final String key) {
        return exclusionDictionary.isEmpty() ? null : exclusionDictionary.get(key);
    }
}
