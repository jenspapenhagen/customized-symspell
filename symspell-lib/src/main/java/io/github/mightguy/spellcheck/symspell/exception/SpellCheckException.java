
package io.github.mightguy.spellcheck.symspell.exception;

/**
 * Checked exception thrown when a spell check operation cannot be completed.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellCheckException extends Exception {

    /**
     * The code that classifies the failure.
     */
    private final SpellCheckExceptionCode spellCheckExceptionCode;

    /**
     * The message supplied by the caller, independent of the exception's own message.
     */
    private final String customMessage;

    /**
     * Returns the error code of this exception.
     *
     * @return the error code of this exception
     */
    public SpellCheckExceptionCode getSpellCheckExceptionCode() {
        return spellCheckExceptionCode;
    }

    /**
     * Returns the message provided by the caller of this exception.
     *
     * @return the message provided by the caller of this exception
     */
    public String getCustomMessage() {
        return customMessage;
    }

    /**
     * Creates an exception for the given error code without a message.
     *
     * @param code the error code describing the failure
     */
    public SpellCheckException(final SpellCheckExceptionCode code) {
        super();
        this.spellCheckExceptionCode = code;
        this.customMessage = "";
    }

    /**
     * Creates an exception for the given error code and message.
     *
     * @param code the error code describing the failure
     * @param message the human readable detail message
     */
    public SpellCheckException(final SpellCheckExceptionCode code, final String message) {
        super(message);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the human readable detail message
     * @param cause the underlying failure
     * @param code the error code describing the failure
     */
    public SpellCheckException(final String message, final Throwable cause, final SpellCheckExceptionCode code) {
        super(message, cause);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }

    /**
     * Creates an exception that wraps a cause and uses its message.
     *
     * @param cause the underlying failure
     * @param code the error code describing the failure
     */
    public SpellCheckException(final Throwable cause, final SpellCheckExceptionCode code) {
        super(cause);
        this.spellCheckExceptionCode = code;
        this.customMessage = cause.getMessage();
    }

    /**
     * Creates an exception with full control over suppression and stack trace writing.
     *
     * @param message the human readable detail message
     * @param cause the underlying failure
     * @param enableSuppression whether suppression is enabled
     * @param writableStackTrace whether the stack trace is writable
     * @param code the error code describing the failure
     */
    protected SpellCheckException(final String message, final Throwable cause, final boolean enableSuppression,
            final boolean writableStackTrace, final SpellCheckExceptionCode code) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.spellCheckExceptionCode = code;
        this.customMessage = message;
    }
}
