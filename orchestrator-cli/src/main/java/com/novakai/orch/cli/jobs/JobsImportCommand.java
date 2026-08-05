package com.novakai.orch.cli.jobs;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "import", description = {"Import a job definition from file"})
public class JobsImportCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to exported JSON/YAML file")
    private Path file;

    @Option(names = {"--team-id"}, description = "Target team ID (X-Team-Id header)")
    private Long teamId;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private HttpClient httpClient;

    public JobsImportCommand() {
        this(HttpClient.newHttpClient());
    }

    public JobsImportCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        if (!java.nio.file.Files.exists(file)) {
            System.err.println("File not found: " + file);
            return 1;
        }

        String effectiveToken = getToken();
        String content = java.nio.file.Files.readString(file);
        String contentType = detectContentType(file.toString());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/jobs/import"))
                .header("Authorization", "Bearer " + effectiveToken)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(content));

        if (teamId != null) {
            builder.header("X-Team-Id", String.valueOf(teamId));
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
            return 1;
        }

        if (response.statusCode() == 400) {
            // Parse error message from response body
            try {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                System.err.println("Import failed: " + root.path("error").asText("Unknown validation error"));
            } catch (Exception e) {
                System.err.println("Import failed: " + response.body());
            }
            return 1;
        }

        System.out.println("Job imported successfully from " + file.getFileName());
        return 0;
    }

    private String detectContentType(String fileName) {
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return "text/yaml";
        }
        return "application/json";
    }

    private String getToken() throws IOException {
        String envToken = System.getenv("ORCHESTRATOR_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken;
        if (token != null && !token.isEmpty()) return token;

        java.nio.file.Path tokenFile = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".orchestrator", "token");
        if (java.nio.file.Files.exists(tokenFile)) {
            String cached = java.nio.file.Files.readString(tokenFile).trim();
            if (!cached.isEmpty()) return cached;
        }

        throw new IllegalStateException("No authentication token found. Run 'orch login' first, " +
                "or set ORCHESTRATOR_TOKEN environment variable.");
    }
}
