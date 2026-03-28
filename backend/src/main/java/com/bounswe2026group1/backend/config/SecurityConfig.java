package com.bounswe2026group1.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // REST API için CSRF korumasını kapat
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register").permitAll() // Kayıt endpointine herkes erişebilir
                        .anyRequest().permitAll() // Şimdilik diğer tüm istekleri de açık bırakıyoruz (ihtiyaca göre değiştirebilirsiniz)
                );
        return http.build();
    }
}
