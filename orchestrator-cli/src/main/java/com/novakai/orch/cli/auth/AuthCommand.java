package com.novakai.orch.cli.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "login", description = {"Authenticate and cache JWT token."})
public class AuthCommand implements Callable<Integer> {

    @Option(names = {"-u", "--user"}, required = true, description = "Username")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Password (prompts if omitted)")
    private String password;

    @Option(names = {"-s", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    private final ObjectMapper objectMapper;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private java.util.function.Consumer<String> tokenSaver;

    public AuthCommand() {
        this(new ObjectMapper(), new org.springframework.web.client.RestTemplate());
    }

    // Visible for testing
    public AuthCommand(ObjectMapper objectMapper,
                       org.springframework.web.client.RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /** Set a callback for saving tokens (used in tests). */
    public void setTokenSaver(java.util.function.Consumer<String> saver) {
        this.tokenSaver = saver;
    }

    @Override
    public Integer call() throws Exception {
        String pwd = password;
        if (pwd == null || pwd.isEmpty()) {
            java.io.Console console = System.console();
            if (console != null) {
                char[] chars = console.readPassword("Password: ");
                pwd = new String(chars);
            } else {
                throw new IllegalStateException("Cannot read password from stdin. Use --password flag.");
            }
        }

        // Build login request body
        String requestBody = objectMapper.writeValueAsString(
                new LoginPayload(username, pwd));

        // POST to /api/auth/login
        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(requestBody,
                        new org.springframework.http.HttpHeaders() {{
                            setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                        }});

        String url = serverUrl + "/api/auth/login";

        org.springframework.http.ResponseEntity<String> response =
                restTemplate.postForEntity(url, entity, String.class);

        // Parse response
        JsonNode root = objectMapper.readTree(response.getBody());
        boolean success = root.path("success").asBoolean(false);

        if (!success) {
            System.err.println("Login failed: " + root.path("error").asText("Unknown error"));
            return 1;
        }

        String token = root.path("data").path("token").asText();
        String role = root.path("data").path("role").asText();

        // Save token to file cache
        if (tokenSaver != null) {
            tokenSaver.accept(token);
        } else {
            saveTokenToFile(token);
        }

        System.out.println("Login successful. Token cached.");
        System.out.println("Role: " + role);

        return 0;
    }

    private void saveTokenToFile(String token) throws IOException {
        java.nio.file.Path dir = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".orchestrator");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("token"), token);
    }

    public record LoginPayload(String username, String password) {}
}
