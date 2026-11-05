package io.github.mightguy.microserivce.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Generic JSON response envelope returned by the spellcheck service.
 *
 * @author MighTguY
 * @since 6.7
 */
public class Response {

    protected HttpStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    protected String message;
    protected Object resp;

    /**
     * Creates an empty response, required for JSON deserialization.
     */
    public Response() {
    }

    /**
     * Creates a response without a payload.
     *
     * @param theStatus the HTTP status
     * @param theMessage the human readable message
     */
    public Response(final HttpStatus theStatus, final String theMessage) {
        this.status = theStatus;
        this.message = theMessage;
    }

    /**
     *
     * @param status
     * @param message
     * @param resp
     */
    public Response(final HttpStatus theStatus, final String theMessage, final Object theResp) {
        this.status = theStatus;
        this.message = theMessage;
        this.resp = theResp;
    }

    /**
     * Returns the HTTP status.
     *
     * @return the status, or {@code null} if unset
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Sets the HTTP status.
     *
     * @param theStatus the status
     */
    public void setStatus(final HttpStatus theStatus) {
        this.status = theStatus;
    }

    /**
     * Returns the human readable message.
     *
     * @return the message, or {@code null} if unset
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human readable message.
     *
     * @param theMessage the message
     */
    public void setMessage(final String theMessage) {
        this.message = theMessage;
    }

    /**
     * Returns the payload.
     *
     * @return the payload, or {@code null} if unset
     */
    public Object getResp() {
        return resp;
    }

    /**
     * Sets the payload.
     *
     * @param theResp the payload
     */
    public void setResp(final Object theResp) {
        this.resp = theResp;
    }
}