package com.shortener.dto;

public record RegisterRequest(
    String email,
    String password
) {}
