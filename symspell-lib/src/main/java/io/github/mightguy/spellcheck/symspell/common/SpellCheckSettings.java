
package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.SpellChecker;

/**
 * SpellCheckSetting contians all the setting used by {@link SpellChecker}.
  *
 * @author MighTguY
 * @since 6.7
 */
public class SpellCheckSettings {
    private static final int DEFAULT_TOP_K = 10;
    private static final int DEFAULT_PREFIX_LENGTH = 7;
    private static final double DEFAULT_EDIT_FACTOR = 0.3;



    /**
     * Default verbosity {@link Verbosity}.
     */
    private Verbosity verbosity = Verbosity.ALL;

    /**
     * limit suggestion list to topK entries.
     */
    private int topK = DEFAULT_TOP_K; // limits result to n entries


    /**
     * Damerau function variables Deletion weight: 1.20 ~ 1.40.
     */
    private float deletionWeight = 1.0f;

    /**
     * Damerau function variables Insertion weight: 1.01.
     */
    private float insertionWeight = 1.0f;

    /**
     * Damerau function variables Replace weight: 0.9f ~ 1.20.
     */
    private float replaceWeight = 1.0f;
    /**
     * Damerau function variables Transposition weight: 0.7f ~ 1.05.
     */
    private float transpositionWeight = 1.0f;


    /**
     * true if the spellchecker should lowercase terms.
     */
    private boolean lowerCaseTerms = true;


    /**
     * Maximum edit distance for doing lookups. (default 2.0)
     */
    private double maxEditDistance = 2;

    /**
     * The length of word prefixes used for spell checking. (default 7)
     */
    private int prefixLength = DEFAULT_PREFIX_LENGTH;

    /**
     * The minimum frequency count for dictionary words to be considered correct spellings. (default
     * 1)
     */
    private long countThreshold = 1;

    /**
     * Max keywordLength.
     */
    private int maxLength = Integer.MAX_VALUE;

    private double bigramCountMin = Double.MAX_VALUE;

    /**
     * Ignore the word in resultset, if suggestions are empty.
     */
    private boolean ignoreUnknown = true;

    /**
     * Edit Factor to compute max edit distance possible for a word.
     */
    private double editFactor = DEFAULT_EDIT_FACTOR;

    private boolean doKeySplit = true;

    private String keySplitRegex = "\\s+";

    /**
     * True if terms are folded to ASCII when indexed and looked up (for example German umlauts).
     */
    private boolean transliterate = false;

    /**
     * True if a compound split is only accepted when both parts are real dictionary terms or the
     * bigram is known. Prevents spurious splits such as "Kaffee" into "Kaf" + "fee".
     */
    private boolean noSubMatches = false;

    /**
     * When enabled, {@code lookupCompound} segments a joined word with a Viterbi style dynamic
     * program over dictionary frequencies instead of trying a single split position. The
     * segmentation always covers the complete input, so no character is silently dropped.
     */
    private boolean viterbiSegmentation = false;

    private SpellCheckSettings(final Verbosity verbosityValue, final int topKValue, final float deletionWeightValue,
            final float insertionWeightValue, final float replaceWeightValue, final float transpositionWeightValue,
            final boolean lowerCaseTermsValue, final double maxEditDistanceValue,
            final int prefixLengthValue, final long countThresholdValue,
            final int maxLengthValue, final double bigramCountMinValue,
            final boolean ignoreUnknownValue, final double editFactorValue,
            final boolean doKeySplitValue, final String keySplitRegexValue,
            final boolean transliterateValue, final boolean noSubMatchesValue,
            final boolean viterbiSegmentationValue) {
        this.verbosity = verbosityValue;
        this.topK = topKValue;
        this.deletionWeight = deletionWeightValue;
        this.insertionWeight = insertionWeightValue;
        this.replaceWeight = replaceWeightValue;
        this.transpositionWeight = transpositionWeightValue;
        this.lowerCaseTerms = lowerCaseTermsValue;
        this.maxEditDistance = maxEditDistanceValue;
        this.prefixLength = prefixLengthValue;
        this.countThreshold = countThresholdValue;
        this.maxLength = maxLengthValue;
        this.bigramCountMin = bigramCountMinValue;
        this.ignoreUnknown = ignoreUnknownValue;
        this.editFactor = editFactorValue;
        this.doKeySplit = doKeySplitValue;
        this.keySplitRegex = keySplitRegexValue;
        this.transliterate = transliterateValue;
        this.noSubMatches = noSubMatchesValue;
        this.viterbiSegmentation = viterbiSegmentationValue;
    }

    /**
     * Returns a builder pre-filled with the documented defaults.
     *
     * @return a builder pre-filled with the documented defaults
     */
    public static SpellCheckSettingsBuilder builder() {
        return new SpellCheckSettingsBuilder();
    }

    /**
     * Returns the configured verbosity.
     *
     * @return the configured verbosity
     */
    public Verbosity getVerbosity() {
        return verbosity;
    }

    /**
     * Returns the maximum number of suggestions returned.
     *
     * @return the maximum number of suggestions returned
     */
    public int getTopK() {
        return topK;
    }

    /**
     * Returns the deletion weight of the distance metric.
     *
     * @return the deletion weight of the distance metric
     */
    public float getDeletionWeight() {
        return deletionWeight;
    }

    /**
     * Returns the insertion weight of the distance metric.
     *
     * @return the insertion weight of the distance metric
     */
    public float getInsertionWeight() {
        return insertionWeight;
    }

    /**
     * Returns the replace weight of the distance metric.
     *
     * @return the replace weight of the distance metric
     */
    public float getReplaceWeight() {
        return replaceWeight;
    }

    /**
     * Returns the transposition weight of the distance metric.
     *
     * @return the transposition weight of the distance metric
     */
    public float getTranspositionWeight() {
        return transpositionWeight;
    }

    /**
     * Returns true if terms are lowercased before lookup.
     *
     * @return true if terms are lowercased before lookup
     */
    public boolean isLowerCaseTerms() {
        return lowerCaseTerms;
    }

    /**
     * Returns the maximum edit distance used for lookups.
     *
     * @return the maximum edit distance used for lookups
     */
    public double getMaxEditDistance() {
        return maxEditDistance;
    }

    /**
     * Returns the length of the indexed word prefixes.
     *
     * @return the length of the indexed word prefixes
     */
    public int getPrefixLength() {
        return prefixLength;
    }

    /**
     * Returns the minimum frequency of a dictionary word.
     *
     * @return the minimum frequency of a dictionary word
     */
    public long getCountThreshold() {
        return countThreshold;
    }

    /**
     * Returns the maximum length of an indexed key.
     *
     * @return the maximum length of an indexed key
     */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the maximum length of an indexed key.
     *
     * @param maxLengthValue the maximum length of an indexed key
     */
    public void setMaxLength(final int maxLengthValue) {
        this.maxLength = maxLengthValue;
    }

    /**
     * Returns the minimum count of a bigram.
     *
     * @return the minimum count of a bigram
     */
    public double getBigramCountMin() {
        return bigramCountMin;
    }

    /**
     * Sets the minimum count of a bigram.
     *
     * @param bigramCountMinValue the minimum count of a bigram
     */
    public void setBigramCountMin(final double bigramCountMinValue) {
        this.bigramCountMin = bigramCountMinValue;
    }

    /**
     * Returns true if unknown words are dropped from the result set.
     *
     * @return true if unknown words are dropped from the result set
     */
    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    /**
     * Returns the factor used to derive the edit distance from the word length.
     *
     * @return the factor used to derive the edit distance from the word length
     */
    public double getEditFactor() {
        return editFactor;
    }

    /**
     * Returns true if multi word keys are split on whitespace.
     *
     * @return true if multi word keys are split on whitespace
     */
    public boolean isDoKeySplit() {
        return doKeySplit;
    }

    /**
     * Returns the regular expression used to split multi word keys.
     *
     * @return the regular expression used to split multi word keys
     */
    public String getKeySplitRegex() {
        return keySplitRegex;
    }

    /**
     * Returns whether terms are folded to ASCII before indexing and lookup.
     *
     * @return true if transliteration is enabled
     */
    public boolean isTransliterate() {
        return transliterate;
    }

    /**
     * Returns true if compound splits require real dictionary parts.
     *
     * @return true if compound splits require real dictionary parts
     */
    public boolean isNoSubMatches() {
        return noSubMatches;
    }

    /**
     * Sets true to reject splits whose parts are not dictionary terms.
     *
     * @param noSubMatchesValue true to reject splits whose parts are not dictionary terms
     */
    public void setNoSubMatches(final boolean noSubMatchesValue) {
        this.noSubMatches = noSubMatchesValue;
    }

    /**
     * Returns true if compound segmentation uses the Viterbi dynamic program.
     *
     * @return true if compound segmentation uses the Viterbi dynamic program
     */
    public boolean isViterbiSegmentation() {
        return viterbiSegmentation;
    }

    /**
     * Sets true to segment joined words with the Viterbi dynamic program.
     *
     * @param viterbiSegmentationValue true to segment joined words with the Viterbi dynamic program
     */
    public void setViterbiSegmentation(final boolean viterbiSegmentationValue) {
        this.viterbiSegmentation = viterbiSegmentationValue;
    }

    @Override
    public String toString() {
        return "SpellCheckSettings(verbosity=" + verbosity
                + ", topK=" + topK
                + ", deletionWeight=" + deletionWeight
                + ", insertionWeight=" + insertionWeight
                + ", replaceWeight=" + replaceWeight
                + ", transpositionWeight=" + transpositionWeight
                + ", lowerCaseTerms=" + lowerCaseTerms
                + ", maxEditDistance=" + maxEditDistance
                + ", prefixLength=" + prefixLength
                + ", countThreshold=" + countThreshold
                + ", maxLength=" + maxLength
                + ", bigramCountMin=" + bigramCountMin
                + ", ignoreUnknown=" + ignoreUnknown
                + ", editFactor=" + editFactor
                + ", doKeySplit=" + doKeySplit
                + ", keySplitRegex=" + keySplitRegex
                + ", transliterate=" + transliterate
                + ", noSubMatches=" + noSubMatches
                + ", viterbiSegmentation=" + viterbiSegmentation + ")";
    }

    /**
     * Builder of {@link SpellCheckSettings}. Every field starts with its documented default, so
     * {@code SpellCheckSettings.builder().build()} is equivalent to a default configuration.
     */
    public static final class SpellCheckSettingsBuilder {

        /**
         * Creates a builder with the documented default values.
         */
        public SpellCheckSettingsBuilder() {
            // defaults are field initialisers
        }

        private Verbosity verbosity = Verbosity.ALL;
        private int topK = DEFAULT_TOP_K;
        private float deletionWeight = 1.0f;
        private float insertionWeight = 1.0f;
        private float replaceWeight = 1.0f;
        private float transpositionWeight = 1.0f;
        private boolean lowerCaseTerms = true;
        private double maxEditDistance = 2;
        private int prefixLength = DEFAULT_PREFIX_LENGTH;
        private long countThreshold = 1;
        private int maxLength = Integer.MAX_VALUE;
        private double bigramCountMin = Double.MAX_VALUE;
        private boolean ignoreUnknown = true;
        private double editFactor = DEFAULT_EDIT_FACTOR;
        private boolean doKeySplit = true;
        private String keySplitRegex = "\\s+";
        private boolean transliterate = false;

        private boolean noSubMatches = false;

        private boolean viterbiSegmentation = false;

        /**
         * Configures the verbosity used for lookups.
         *
         * @param verbosityValue the verbosity used for lookups
         * @return this builder
         */
        public SpellCheckSettingsBuilder verbosity(final Verbosity verbosityValue) {
            this.verbosity = verbosityValue;
            return this;
        }

        /**
         * Configures the maximum number of suggestions.
         *
         * @param topKValue the maximum number of suggestions
         * @return this builder
         */
        public SpellCheckSettingsBuilder topK(final int topKValue) {
            this.topK = topKValue;
            return this;
        }

        /**
         * Configures the deletion weight.
         *
         * @param deletionWeightValue the deletion weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder deletionWeight(final float deletionWeightValue) {
            this.deletionWeight = deletionWeightValue;
            return this;
        }

        /**
         * Configures the insertion weight.
         *
         * @param insertionWeightValue the insertion weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder insertionWeight(final float insertionWeightValue) {
            this.insertionWeight = insertionWeightValue;
            return this;
        }

        /**
         * Configures the replace weight.
         *
         * @param replaceWeightValue the replace weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder replaceWeight(final float replaceWeightValue) {
            this.replaceWeight = replaceWeightValue;
            return this;
        }

        /**
         * Configures the transposition weight.
         *
         * @param transpositionWeightValue the transposition weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder transpositionWeight(final float transpositionWeightValue) {
            this.transpositionWeight = transpositionWeightValue;
            return this;
        }

        /**
         * Configures true if terms should be lowercased.
         *
         * @param lowerCaseTermsValue true if terms should be lowercased
         * @return this builder
         */
        public SpellCheckSettingsBuilder lowerCaseTerms(final boolean lowerCaseTermsValue) {
            this.lowerCaseTerms = lowerCaseTermsValue;
            return this;
        }

        /**
         * Configures the maximum edit distance.
         *
         * @param maxEditDistanceValue the maximum edit distance
         * @return this builder
         */
        public SpellCheckSettingsBuilder maxEditDistance(final double maxEditDistanceValue) {
            this.maxEditDistance = maxEditDistanceValue;
            return this;
        }

        /**
         * Configures the length of the indexed prefixes.
         *
         * @param prefixLengthValue the length of the indexed prefixes
         * @return this builder
         */
        public SpellCheckSettingsBuilder prefixLength(final int prefixLengthValue) {
            this.prefixLength = prefixLengthValue;
            return this;
        }

        /**
         * Configures the minimum frequency of a dictionary word.
         *
         * @param countThresholdValue the minimum frequency of a dictionary word
         * @return this builder
         */
        public SpellCheckSettingsBuilder countThreshold(final long countThresholdValue) {
            this.countThreshold = countThresholdValue;
            return this;
        }

        /**
         * Configures the maximum length of an indexed key.
         *
         * @param maxLengthValue the maximum length of an indexed key
         * @return this builder
         */
        public SpellCheckSettingsBuilder maxLength(final int maxLengthValue) {
            this.maxLength = maxLengthValue;
            return this;
        }

        /**
         * Configures the minimum count of a bigram.
         *
         * @param bigramCountMinValue the minimum count of a bigram
         * @return this builder
         */
        public SpellCheckSettingsBuilder bigramCountMin(final double bigramCountMinValue) {
            this.bigramCountMin = bigramCountMinValue;
            return this;
        }

        /**
         * Configures true if unknown words are dropped.
         *
         * @param ignoreUnknownValue true if unknown words are dropped
         * @return this builder
         */
        public SpellCheckSettingsBuilder ignoreUnknown(final boolean ignoreUnknownValue) {
            this.ignoreUnknown = ignoreUnknownValue;
            return this;
        }

        /**
         * Configures the factor used to derive the edit distance.
         *
         * @param editFactorValue the factor used to derive the edit distance
         * @return this builder
         */
        public SpellCheckSettingsBuilder editFactor(final double editFactorValue) {
            this.editFactor = editFactorValue;
            return this;
        }

        /**
         * Configures true if multi word keys are split.
         *
         * @param doKeySplitValue true if multi word keys are split
         * @return this builder
         */
        public SpellCheckSettingsBuilder doKeySplit(final boolean doKeySplitValue) {
            this.doKeySplit = doKeySplitValue;
            return this;
        }

        /**
         * Configures the regular expression used to split multi word keys.
         *
         * @param keySplitRegexValue the regular expression used to split multi word keys
         * @return this builder
         */
        public SpellCheckSettingsBuilder keySplitRegex(final String keySplitRegexValue) {
            this.keySplitRegex = keySplitRegexValue;
            return this;
        }

        /**
         * Enables or disables folding of terms to ASCII.
         *
         * @param transliterateValue true to fold umlauts and other diacritics to ASCII
         * @return this builder
         */
        public SpellCheckSettingsBuilder transliterate(final boolean transliterateValue) {
            this.transliterate = transliterateValue;
            return this;
        }

        /**
         * Configures true to reject splits whose parts are not dictionary terms.
         *
         * @param noSubMatchesValue true to reject splits whose parts are not dictionary terms
         * @return this builder
         */
        public SpellCheckSettingsBuilder noSubMatches(final boolean noSubMatchesValue) {
            this.noSubMatches = noSubMatchesValue;
            return this;
        }

        /**
         * Enables Viterbi style compound segmentation over dictionary frequencies.
         *
         * @param viterbiSegmentationValue true to segment joined words with the dynamic program
         * @return this builder
         */
        public SpellCheckSettingsBuilder viterbiSegmentation(final boolean viterbiSegmentationValue) {
            this.viterbiSegmentation = viterbiSegmentationValue;
            return this;
        }

        /**
         * Returns the immutable-by-convention settings.
         *
         * @return the immutable-by-convention settings
         */
        public SpellCheckSettings build() {
            return new SpellCheckSettings(verbosity, topK, deletionWeight, insertionWeight,
                    replaceWeight, transpositionWeight, lowerCaseTerms, maxEditDistance, prefixLength,
                    countThreshold, maxLength, bigramCountMin, ignoreUnknown, editFactor, doKeySplit,
                    keySplitRegex, transliterate, noSubMatches, viterbiSegmentation);
        }
    }
}
