package com.novakai.orch.cli.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private final AtomicReference<String> capturedToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
    }

    @Test
    void login_success_saves_token() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"token\":\"test-jwt-token\",\"role\":\"ADMIN\",\"passwordExpired\":false}}";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        AuthCommand cmd = createAuthCommand(restTemplate, "admin", "changeme", "http://localhost:8080");
        cmd.setTokenSaver(capturedToken::set);

        int exitCode = cmd.call();

        assertEquals(0, exitCode);
        assertEquals("test-jwt-token", capturedToken.get());

        verify(restTemplate).postForEntity(eq("http://localhost:8080/api/auth/login"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void login_bad_credentials_returns_1() throws Exception {
        String mockResponse = "{\"success\":false,\"error\":\"Invalid username or password\"}";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        AuthCommand cmd = createAuthCommand(restTemplate, "admin", "wrongpass", "http://localhost:8080");

        int exitCode = cmd.call();

        assertEquals(1, exitCode);
        assertNull(capturedToken.get());
    }

    @Test
    void login_sends_correct_request_body() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"token\":\"tok\",\"role\":\"VIEWER\",\"passwordExpired\":false}}";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        AuthCommand cmd = createAuthCommand(restTemplate, "admin", "changeme", "http://localhost:8080");

        int exitCode = cmd.call();

        assertEquals(0, exitCode);

        // Verify the request body contains username and password
        verify(restTemplate).postForEntity(eq("http://localhost:8080/api/auth/login"), argThat(entity -> {
            String body = (String) ((HttpEntity<?>) entity).getBody();
            return body.contains("\"username\":\"admin\"") && body.contains("\"password\":\"changeme\"");
        }), eq(String.class));
    }

    private AuthCommand createAuthCommand(RestTemplate rt, String username, String password, String serverUrl) {
        AuthCommand cmd = new AuthCommand(mapper, rt);
        setField(cmd, "username", username);
        setField(cmd, "password", password);
        setField(cmd, "serverUrl", serverUrl);
        return cmd;
    }

    private void setField(Object obj, String name, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
