package com.shortener.security;

import com.shortener.entity.Provider;
import com.shortener.entity.Role;
import com.shortener.entity.User;
import com.shortener.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class CustomOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public CustomOAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oAuth2User = oauthToken.getPrincipal();
            String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();

            String providerStr = clientRegistrationId.toUpperCase();
            Provider provider = Provider.valueOf(providerStr);

            String email = oAuth2User.getAttribute("email");
            if (email == null) {
                String login = oAuth2User.getAttribute("login");
                email = login != null ? login + "@github.com" : "oauth-" + provider.name().toLowerCase() + "-" + System.currentTimeMillis() + "@vectorlink.local";
            }

            String providerId = null;
            if (Provider.GOOGLE.equals(provider)) {
                providerId = oAuth2User.getAttribute("sub");
            } else if (Provider.GITHUB.equals(provider)) {
                Object idObj = oAuth2User.getAttribute("id");
                providerId = idObj != null ? String.valueOf(idObj) : null;
            }

            if (providerId == null) {
                response.sendRedirect("/login.html?error=OAuth2 provider identity could not be retrieved");
                return;
            }

            Optional<User> userOpt = userRepository.findByProviderAndProviderId(provider, providerId);
            Optional<User> userByEmailOpt = userRepository.findByEmail(email);

            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
                if (!email.equals(user.getEmail())) {
                    user.setEmail(email);
                    userRepository.save(user);
                }
            } else if (userByEmailOpt.isPresent()) {
                user = userByEmailOpt.get();
                user.setProvider(provider);
                user.setProviderId(providerId);
                userRepository.save(user);
            } else {
                user = User.builder()
                        .email(email)
                        .provider(provider)
                        .providerId(providerId)
                        .role(Role.USER)
                        .build();
                userRepository.save(user);
            }

            String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

            Cookie cookie = new Cookie("vlink_jwt", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 24 hours
            cookie.setAttribute("SameSite", "Strict");
            if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
                cookie.setSecure(true);
            }
            response.addCookie(cookie);
        }

        response.sendRedirect("/dashboard.html");
    }
}
