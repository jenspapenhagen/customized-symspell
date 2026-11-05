
package io.github.mightguy.spellcheck.symspell.exception;

/**
 * Classifies the reason a spell check operation failed.
 *
 * @author MighTguY
 * @since 6.7
 */
public enum SpellCheckExceptionCode {

    LOOKUP_ERROR("Exception occured while looking up the term"),
    INDEX_ERROR("Exception occured while indexing  the term"),
    DELETE_ERROR("Exception occured while deleting up the term"),
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
