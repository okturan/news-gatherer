package com.newsgatherer.infrastructure.http;

/**
 * Exception thrown when GDELT API communication fails.
 */
public class GdeltApiException extends Exception {

    public GdeltApiException(String message) {
        super(message);
    }

    public GdeltApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
