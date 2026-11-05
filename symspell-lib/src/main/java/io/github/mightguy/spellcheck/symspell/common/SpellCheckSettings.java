
package io.github.mightguy.spellcheck.symspell.common;

import io.github.mightguy.spellcheck.symspell.api.SpellChecker;

/**
 * SpellCheckSetting contians all the setting used by {@link SpellChecker}.
  *
 * @author MighTguY
 * @since 6.7
 */
public class SpellCheckSettings {
    private static final int MAGIC_10 = 10;
    private static final int MAGIC_7 = 7;
    private static final double MAGIC_0_3 = 0.3;



    /**
     * Default verbosity {@link Verbosity}.
     */
    private Verbosity verbosity = Verbosity.ALL;

    /**
     * limit suggestion list to topK entries.
     */
    private int topK = MAGIC_10; // limits result to n entries


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
    private int prefixLength = MAGIC_7;

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
    private double editFactor = MAGIC_0_3;

    private boolean doKeySplit = true;

    private String keySplitRegex = "\\s+";

    /**
     * True if terms are folded to ASCII when indexed and looked up (for example German umlauts).
     */
    private boolean transliterate = false;

    private SpellCheckSettings(final Verbosity verbosityValue, final int topKValue, final float deletionWeightValue,
            final float insertionWeightValue, final float replaceWeightValue, final float transpositionWeightValue,
            final boolean lowerCaseTermsValue, final double maxEditDistanceValue,
            final int prefixLengthValue, final long countThresholdValue,
            final int maxLengthValue, final double bigramCountMinValue,
            final boolean ignoreUnknownValue, final double editFactorValue,
            final boolean doKeySplitValue, final String keySplitRegexValue,
            final boolean transliterateValue) {
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
    }

    /**
     * @return a builder pre-filled with the documented defaults
     */
    public static SpellCheckSettingsBuilder builder() {
        return new SpellCheckSettingsBuilder();
    }

    /**
     * @return the configured verbosity
     */
    public Verbosity getVerbosity() {
        return verbosity;
    }

    /**
     * @return the maximum number of suggestions returned
     */
    public int getTopK() {
        return topK;
    }

    /**
     * @return the deletion weight of the distance metric
     */
    public float getDeletionWeight() {
        return deletionWeight;
    }

    /**
     * @return the insertion weight of the distance metric
     */
    public float getInsertionWeight() {
        return insertionWeight;
    }

    /**
     * @return the replace weight of the distance metric
     */
    public float getReplaceWeight() {
        return replaceWeight;
    }

    /**
     * @return the transposition weight of the distance metric
     */
    public float getTranspositionWeight() {
        return transpositionWeight;
    }

    /**
     * @return true if terms are lowercased before lookup
     */
    public boolean isLowerCaseTerms() {
        return lowerCaseTerms;
    }

    /**
     * @return the maximum edit distance used for lookups
     */
    public double getMaxEditDistance() {
        return maxEditDistance;
    }

    /**
     * @return the length of the indexed word prefixes
     */
    public int getPrefixLength() {
        return prefixLength;
    }

    /**
     * @return the minimum frequency of a dictionary word
     */
    public long getCountThreshold() {
        return countThreshold;
    }

    /**
     * @return the maximum length of an indexed key
     */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * @param maxLength the maximum length of an indexed key
     */
    public void setMaxLength(final int maxLengthValue) {
        this.maxLength = maxLengthValue;
    }

    /**
     * @return the minimum count of a bigram
     */
    public double getBigramCountMin() {
        return bigramCountMin;
    }

    /**
     * @param bigramCountMin the minimum count of a bigram
     */
    public void setBigramCountMin(final double bigramCountMinValue) {
        this.bigramCountMin = bigramCountMinValue;
    }

    /**
     * @return true if unknown words are dropped from the result set
     */
    public boolean isIgnoreUnknown() {
        return ignoreUnknown;
    }

    /**
     * @return the factor used to derive the edit distance from the word length
     */
    public double getEditFactor() {
        return editFactor;
    }

    /**
     * @return true if multi word keys are split on whitespace
     */
    public boolean isDoKeySplit() {
        return doKeySplit;
    }

    /**
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
                + ", transliterate=" + transliterate + ")";
    }

    /**
     * Builder of {@link SpellCheckSettings}. Every field starts with its documented default, so
     * {@code SpellCheckSettings.builder().build()} is equivalent to a default configuration.
     */
    public static final class SpellCheckSettingsBuilder {

        private Verbosity verbosity = Verbosity.ALL;
        private int topK = MAGIC_10;
        private float deletionWeight = 1.0f;
        private float insertionWeight = 1.0f;
        private float replaceWeight = 1.0f;
        private float transpositionWeight = 1.0f;
        private boolean lowerCaseTerms = true;
        private double maxEditDistance = 2;
        private int prefixLength = MAGIC_7;
        private long countThreshold = 1;
        private int maxLength = Integer.MAX_VALUE;
        private double bigramCountMin = Double.MAX_VALUE;
        private boolean ignoreUnknown = true;
        private double editFactor = MAGIC_0_3;
        private boolean doKeySplit = true;
        private String keySplitRegex = "\\s+";
        private boolean transliterate = false;

        /**
         * @param verbosity the verbosity used for lookups
         * @return this builder
         */
        public SpellCheckSettingsBuilder verbosity(final Verbosity verbosityValue) {
            this.verbosity = verbosityValue;
            return this;
        }

        /**
         * @param topK the maximum number of suggestions
         * @return this builder
         */
        public SpellCheckSettingsBuilder topK(final int topKValue) {
            this.topK = topKValue;
            return this;
        }

        /**
         * @param deletionWeight the deletion weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder deletionWeight(final float deletionWeightValue) {
            this.deletionWeight = deletionWeightValue;
            return this;
        }

        /**
         * @param insertionWeight the insertion weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder insertionWeight(final float insertionWeightValue) {
            this.insertionWeight = insertionWeightValue;
            return this;
        }

        /**
         * @param replaceWeight the replace weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder replaceWeight(final float replaceWeightValue) {
            this.replaceWeight = replaceWeightValue;
            return this;
        }

        /**
         * @param transpositionWeight the transposition weight
         * @return this builder
         */
        public SpellCheckSettingsBuilder transpositionWeight(final float transpositionWeightValue) {
            this.transpositionWeight = transpositionWeightValue;
            return this;
        }

        /**
         * @param lowerCaseTerms true if terms should be lowercased
         * @return this builder
         */
        public SpellCheckSettingsBuilder lowerCaseTerms(final boolean lowerCaseTermsValue) {
            this.lowerCaseTerms = lowerCaseTermsValue;
            return this;
        }

        /**
         * @param maxEditDistance the maximum edit distance
         * @return this builder
         */
        public SpellCheckSettingsBuilder maxEditDistance(final double maxEditDistanceValue) {
            this.maxEditDistance = maxEditDistanceValue;
            return this;
        }

        /**
         * @param prefixLength the length of the indexed prefixes
         * @return this builder
         */
        public SpellCheckSettingsBuilder prefixLength(final int prefixLengthValue) {
            this.prefixLength = prefixLengthValue;
            return this;
        }

        /**
         * @param countThreshold the minimum frequency of a dictionary word
         * @return this builder
         */
        public SpellCheckSettingsBuilder countThreshold(final long countThresholdValue) {
            this.countThreshold = countThresholdValue;
            return this;
        }

        /**
         * @param maxLength the maximum length of an indexed key
         * @return this builder
         */
        public SpellCheckSettingsBuilder maxLength(final int maxLengthValue) {
            this.maxLength = maxLengthValue;
            return this;
        }

        /**
         * @param bigramCountMin the minimum count of a bigram
         * @return this builder
         */
        public SpellCheckSettingsBuilder bigramCountMin(final double bigramCountMinValue) {
            this.bigramCountMin = bigramCountMinValue;
            return this;
        }

        /**
         * @param ignoreUnknown true if unknown words are dropped
         * @return this builder
         */
        public SpellCheckSettingsBuilder ignoreUnknown(final boolean ignoreUnknownValue) {
            this.ignoreUnknown = ignoreUnknownValue;
            return this;
        }

        /**
         * @param editFactor the factor used to derive the edit distance
         * @return this builder
         */
        public SpellCheckSettingsBuilder editFactor(final double editFactorValue) {
            this.editFactor = editFactorValue;
            return this;
        }

        /**
         * @param doKeySplit true if multi word keys are split
         * @return this builder
         */
        public SpellCheckSettingsBuilder doKeySplit(final boolean doKeySplitValue) {
            this.doKeySplit = doKeySplitValue;
            return this;
        }

        /**
         * @param keySplitRegex the regular expression used to split multi word keys
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
         * @return the immutable-by-convention settings
         */
        public SpellCheckSettings build() {
            return new SpellCheckSettings(verbosity, topK, deletionWeight, insertionWeight,
                    replaceWeight, transpositionWeight, lowerCaseTerms, maxEditDistance, prefixLength,
                    countThreshold, maxLength, bigramCountMin, ignoreUnknown, editFactor, doKeySplit,
                    keySplitRegex, transliterate);
        }
    }
}
