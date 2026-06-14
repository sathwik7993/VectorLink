package com.shortener.dto;

public record AuthResponse(
    String email,
    String role,
    String message
) {}
