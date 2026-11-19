package com.urlshortener.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<String> handleInvalidRequest(InvalidRequestException e) {
        log.warn("Rejected request with 400: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(CustomCodeTakenException.class)
    public ResponseEntity<String> handleCustomCodeTaken(CustomCodeTakenException e) {
        log.warn("Rejected request with 409: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
