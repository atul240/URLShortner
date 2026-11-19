package com.urlshortener.exception;

/** The requested custom short code already exists. Mapped to HTTP 409. */
public class CustomCodeTakenException extends RuntimeException {
    public CustomCodeTakenException(String message) {
        super(message);
    }
}
