package com.shortener.config;

import com.shortener.security.CustomOAuth2AuthenticationSuccessHandler;
import com.shortener.security.JwtAuthenticationFilter;
import com.shortener.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomOAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final int managementPort;

    public SecurityConfig(JwtService jwtService, CustomOAuth2AuthenticationSuccessHandler oAuth2SuccessHandler,
                          @Value("${management.server.port:-1}") int managementPort) {
        this.jwtService = jwtService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.managementPort = managementPort;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + authException.getMessage() + "\"}");
                    } else {
                        response.sendRedirect("/login.html");
                    }
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Actuator is reachable only on the internal management port; never on the public port
                .requestMatchers(request -> managementPort > 0 && request.getLocalPort() == managementPort).permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers("/", "/index.html", "/login.html", "/register.html", "/password.html", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/urls/verify-password").permitAll()
                .requestMatchers("/api/v1/qr/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/{shortCode:[a-zA-Z0-9\\-]{3,30}}").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/urls/**").authenticated()
                .requestMatchers("/api/v1/analytics/**").authenticated()
                .requestMatchers("/api/v1/auth/me").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login.html")
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
