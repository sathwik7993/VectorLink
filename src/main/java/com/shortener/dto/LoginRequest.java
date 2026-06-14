package com.shortener.dto;

public record LoginRequest(
    String email,
    String password
) {}
