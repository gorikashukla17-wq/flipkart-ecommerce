package com.shopkart.service;

/** Wraps a checked SQLException at the service boundary. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
