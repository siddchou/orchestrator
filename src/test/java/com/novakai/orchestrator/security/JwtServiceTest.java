package com.novakai.orchestrator.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        try {
            var secretField = jwtService.getClass().getDeclaredField("secret");
            secretField.setAccessible(true);
            secretField.set(jwtService, "test-secret-key-for-testing-purposes-only-32bytes!!");

            var expiryField = jwtService.getClass().getDeclaredField("expiryHours");
            expiryField.setAccessible(true);
            expiryField.set(jwtService, 8);
        } catch (Exception e) {
            fail("Failed to set up JwtService: " + e.getMessage());
        }

        testUser = new User("testuser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void generateToken_returns_non_empty() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_contains_parts() {
        String token = jwtService.generateToken(testUser);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
    }

    @Test
    void extractUsername_from_valid_token() {
        String token = jwtService.generateToken(testUser);
        String username = jwtService.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    void extractRole_from_valid_token() {
        String token = jwtService.generateToken(testUser);
        String role = jwtService.extractRole(token);
        assertEquals("ROLE_ADMIN", role);
    }

    @Test
    void isTokenValid_true_for_fresh_token() {
        String token = jwtService.generateToken(testUser);
        assertTrue(jwtService.isTokenValid(token, testUser));
    }

    @Test
    void isTokenValid_false_for_wrong_user() {
        String token = jwtService.generateToken(testUser);
        UserDetails otherUser = new User("otheruser", "password",
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void isTokenValid_false_for_malformed_token() {
        assertThrows(Exception.class, () -> jwtService.isTokenValid("invalid.token.here", testUser));
    }

    @Test
    void extractClaim_custom_resolver() {
        String token = jwtService.generateToken(testUser);
        String username = jwtService.extractClaim(token, claims -> claims.getSubject());
        assertEquals("testuser", username);
    }

    @Test
    void generateToken_with_extra_claims() {
        java.util.Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("custom", "value");
        String token = jwtService.generateToken(extraClaims, testUser);
        assertNotNull(token);
        assertEquals("testuser", jwtService.extractUsername(token));
    }
}
