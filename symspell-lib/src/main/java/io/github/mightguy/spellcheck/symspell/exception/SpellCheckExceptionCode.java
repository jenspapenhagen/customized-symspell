
package io.github.mightguy.spellcheck.symspell.exception;

/**
 * Classifies the reason a spell check operation failed.
 *
 * @author MighTguY
 * @since 6.7
 */
public enum SpellCheckExceptionCode {

    /**
     * A lookup of suggestions failed.
     */
    LOOKUP_ERROR("Exception occured while looking up the term"),
    /**
     * Indexing a dictionary entry failed.
     */
    INDEX_ERROR("Exception occured while indexing  the term"),
    /**
     * Generating the deletes of a term failed.
     */
    DELETE_ERROR("Exception occured while deleting up the term"),
    /**
     * Dumping or loading the dictionary failed.
     */
    DUMP_ERROR("Exception occured while dumping up the term");

    private String message;

    SpellCheckExceptionCode(final String theMessage) {
        this.message = theMessage;
    }

    /**
     * Returns the human readable description of this error code.
     *
     * @return the description, never {@code null}
     */
    public String getMessage() {
        return message;
    }
}
