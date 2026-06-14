package com.shortener.service;

import com.shortener.dto.RegisterRequest;
import com.shortener.entity.Provider;
import com.shortener.entity.Role;
import com.shortener.entity.User;
import com.shortener.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (request.email() == null || request.email().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }

        Optional<User> existing = userRepository.findByEmail(request.email());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = User.builder()
                .email(request.email().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
