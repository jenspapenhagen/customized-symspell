
package io.github.mightguy.spellcheck.symspell.exception;

/**
 * Checked exception thrown when a spell check operation cannot be completed.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellCheckException extends Exception {

    private final SpellCheckExceptionCode spellCheckExceptionCode;
    private final String customMessage;

    /**
     * @return the error code of this exception
     */
    public SpellCheckExceptionCode getSpellCheckExceptionCode() {
        return spellCheckExceptionCode;
    }

    /**
     * @return the message provided by the caller of this exception
     */
    public String getCustomMessage() {
        return customMessage;
    }

    /**
     * Exception for SPellcheck.
     * @param code
     */
    public SpellCheckException(final SpellCheckExceptionCode code) {
        super();
        this.spellCheckExceptionCode = code;
        this.customMessage = "";
    }

    /**
     * Exception for SPellcheck.
     * @param code
     * @param message
     */
    public SpellCheckException(final SpellCheckExceptionCode code, final String message) {
        super(message);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }

    /**
     * Exception for SPellcheck.
     * @param message
     * @param cause
     * @param code
     */
    public SpellCheckException(final String message, final Throwable cause, final SpellCheckExceptionCode code) {
        super(message, cause);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }

    /**
     * Exception for SPellcheck.
     * @param cause
     * @param code
     */
    public SpellCheckException(final Throwable cause, final SpellCheckExceptionCode code) {
        super(cause);
        this.spellCheckExceptionCode = code;
        this.customMessage = cause.getMessage();
    }

    /**
     * Exception for SPellcheck.
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     * @param code
     */
    protected SpellCheckException(final String message, final Throwable cause, final boolean enableSuppression,
            final boolean writableStackTrace, final SpellCheckExceptionCode code) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }
}
