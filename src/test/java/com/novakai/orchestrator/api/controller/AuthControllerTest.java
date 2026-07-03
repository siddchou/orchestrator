package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ChangePasswordRequest;
import com.novakai.orchestrator.api.dto.LoginRequest;
import com.novakai.orchestrator.domain.entity.AppUser;
import com.novakai.orchestrator.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
            
        userRepo.deleteAll();
        AppUser admin = AppUser.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin1234"))
                .role("ADMIN")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(admin);

        AppUser viewer = AppUser.builder()
                .username("viewer")
                .passwordHash(passwordEncoder.encode("viewer1234"))
                .role("VIEWER")
                .enabled("Y")
                .passwordExpired("N")
                .build();
        userRepo.save(viewer);

        // Login to get token
        LoginRequest login = new LoginRequest("admin", "admin1234");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/login", login, String.class);
        String body = response.getBody();
        int start = body.indexOf("\"token\":\"") + 9;
        int end = body.indexOf("\"", start);
        token = body.substring(start, end);
    }

    @Test
    void login_valid_credentials() {
        LoginRequest request = new LoginRequest("admin", "admin1234");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/login", request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"SUCCESS\""));
        assertTrue(response.getBody().contains("\"role\":\"ADMIN\""));
    }

    @Test
    void login_wrong_password() {
        LoginRequest request = new LoginRequest("admin", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/login", request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"ERROR\""));
        assertTrue(response.getBody().contains("Invalid username or password"));
    }

    @Test
    void login_nonexistent_user() {
        LoginRequest request = new LoginRequest("ghost", "password");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/login", request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"ERROR\""));
    }

    @Test
    void login_blank_fields_returns_400() {
        LoginRequest request = new LoginRequest("", "");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/login", request, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void change_password_without_auth_returns_error() {
        ChangePasswordRequest change = new ChangePasswordRequest("admin1234", "newpass1234");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/change-password", change, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Authentication required"));
    }

    @Test
    void change_password_blank_fields_returns_400() {
        ChangePasswordRequest change = new ChangePasswordRequest("", "");
        ResponseEntity<String> response = restTemplate.postForEntity(
                base() + "/api/auth/change-password", change, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void get_current_user_without_auth_returns_error() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/auth/me", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Authentication required"));
    }
}
