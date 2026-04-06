package com.bounswe2026group1.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyFilter apiKeyFilter;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Anonymous access: routing API and auth endpoints. */
    private static final String[] ROUTING_AND_AUTH_PUBLIC = {
            "/api/routes", "/api/routes/**",
            "/auth/register", "/auth/login",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROUTING_AND_AUTH_PUBLIC).permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/reports", "/api/reports/**",
                                "/api/comments", "/api/comments/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Before the UsernamePasswordAuthenticationFilter, we want to run our JwtAuthFilter to check for JWT tokens in the request
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            log.warn("401 UNAUTHORIZED {} {}: {}", request.getMethod(), sanitizeUri(request.getRequestURI()), e.getMessage());
                            response.sendError(401, e.getMessage());
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            log.warn("403 FORBIDDEN {} {}: {}", request.getMethod(), sanitizeUri(request.getRequestURI()), e.getMessage());
                            response.sendError(403, e.getMessage());
                        })
                )
                .addFilterAfter(jwtAuthFilter, ApiKeyFilter.class);
        return http.build();
    }

    // Strips CR/LF to prevent log forging via CRLF injection
    private static String sanitizeUri(String uri) {
        return uri.replaceAll("[\r\n]", "_");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
