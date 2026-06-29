package com.novakai.orchestrator.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/system/health").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/**/*.js", "/**/*.css", "/favicon.ico").permitAll()
                        // Viewer + Operator + Admin can read runs and jobs
                        .requestMatchers(HttpMethod.GET, "/api/runs/**").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/jobs/**").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        // Operator + Admin can trigger and cancel runs
                        .requestMatchers(HttpMethod.POST, "/api/jobs/*/run").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/runs/*/cancel").hasAnyRole("OPERATOR", "ADMIN")
                        // Admin-only: system config, job CRUD, env vars, credentials
                        .requestMatchers("/api/system/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/jobs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/jobs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/jobs/**").hasRole("ADMIN")
                        .requestMatchers("/api/env-vars/**").hasRole("ADMIN")
                        .requestMatchers("/api/credentials/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
