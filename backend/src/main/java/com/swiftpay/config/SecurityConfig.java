package com.swiftpay.config;

import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.security.CorsProperties;
import com.swiftpay.security.JwtAuthenticationFilter;
import com.swiftpay.security.JwtService;
import com.swiftpay.security.SecurityErrorHandlers;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT security. CSRF is off because the API uses bearer tokens only (no cookies, no sessions).
 * Public: login, health, OpenAPI docs and /error; everything else needs a valid token.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/error"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   CustomerRepository customerRepository,
                                                   SecurityErrorHandlers errorHandlers) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())          // X-Content-Type-Options: nosniff
                        .frameOptions(f -> f.deny())                            // X-Frame-Options: DENY
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .cacheControl(Customizer.withDefaults()))               // Cache-Control: no-cache, no-store, ...
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandlers).accessDeniedHandler(errorHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, customerRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of(TraceIdFilter.HEADER, "Idempotent-Replayed", "Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Strength 10 matches the seeded demo hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
