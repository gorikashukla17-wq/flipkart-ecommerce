package com.shopkart.service;

/** A user-facing business error (bad input, out of stock, not logged in ...). */
public class ShopException extends RuntimeException {

    public enum Kind { BAD_REQUEST, UNAUTHORIZED, NOT_FOUND, CONFLICT }

    private final Kind kind;

    public ShopException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
