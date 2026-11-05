package io.github.mightguy.symspell.solr.utils;

/**
 * Parameter names understood by the spell check component.
 *
 * @author MighTguY
 * @since 6.7
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Common prefix of every spell check parameter.
     */
    public static final String SPELLCHECK_PREFIX = "cspellcheck.";

    /**
     * Query term to check.
     */
    public static final String SPELLCHECK_Q = SPELLCHECK_PREFIX + "q";

    /**
     * Path of the unigram dictionary.
     */
    public static final String SPELLCHECK_DATALOAD_UNIGRAM = SPELLCHECK_PREFIX + "dataload.unigram";

    /**
     * Path of the bigram dictionary.
     */
    public static final String SPELLCHECK_DATALOAD_BIGRAM = SPELLCHECK_PREFIX + "dataload.bigram";

    /**
     * Minimum number of matches required to run the spell check.
     */
    public static final String SPELLCHECK_THRESHOLD = SPELLCHECK_PREFIX + "threshold";

    /**
     * Enables or disables the component.
     */
    public static final String SPELLCHECK_ENABLE = SPELLCHECK_PREFIX + "enable";

    /**
     * Rebuilds the index on the next searcher event.
     */
    public static final String SPELLCHECK_BUILD = SPELLCHECK_PREFIX + "build";

    /**
     * Switches the output between suggestions and a single corrected term.
     */
    public static final String SPELLCHECK_SOW = SPELLCHECK_PREFIX + "sow";
}
