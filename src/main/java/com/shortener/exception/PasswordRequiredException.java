package com.shortener.exception;

public class PasswordRequiredException extends RuntimeException {
    private final String shortCode;

    public PasswordRequiredException(String shortCode) {
        super("Password required for short code: " + shortCode);
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
