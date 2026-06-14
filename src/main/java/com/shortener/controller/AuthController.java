package com.shortener.controller;

import com.shortener.dto.AuthResponse;
import com.shortener.dto.LoginRequest;
import com.shortener.dto.RegisterRequest;
import com.shortener.entity.User;
import com.shortener.security.JwtService;
import com.shortener.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(user.getEmail(), user.getRole().name(), "User registered successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(null, null, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Optional<User> userOpt = userService.findByEmail(request.email());
        if (userOpt.isEmpty() || userOpt.get().getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, "Invalid email or password"));
        }

        User user = userOpt.get();
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        Cookie cookie = new Cookie("vlink_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24 hours
        cookie.setAttribute("SameSite", "Strict");
        if (servletRequest.isSecure() || "https".equalsIgnoreCase(servletRequest.getHeader("X-Forwarded-Proto"))) {
            cookie.setSecure(true);
        }
        servletResponse.addCookie(cookie);

        return ResponseEntity.ok(new AuthResponse(user.getEmail(), user.getRole().name(), "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("vlink_jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        return ResponseEntity.ok(new AuthResponse(null, null, "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getMe() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();
        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userOpt.get();
        return ResponseEntity.ok(new AuthResponse(user.getEmail(), user.getRole().name(), "Authenticated"));
    }
}
