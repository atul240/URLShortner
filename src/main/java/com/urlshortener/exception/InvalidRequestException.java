package com.urlshortener.exception;

/** The client sent input that fails validation. Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
