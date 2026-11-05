
package io.github.mightguy.symspell.solr.utils;

/**
 * Unchecked exception used to abort request handling on unrecoverable errors.
 *
 * @author MighTguY
 * @since 6.7
 */
public class FatalException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public FatalException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public FatalException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param cause the underlying cause
     */
    public FatalException(final Throwable cause) {
        super(cause);
    }

}
