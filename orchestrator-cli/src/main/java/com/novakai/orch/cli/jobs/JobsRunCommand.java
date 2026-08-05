package com.novakai.orch.cli.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "run", description = {"Trigger a job run"})
public class JobsRunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Job ID (numeric) or name (string)")
    private String jobIdOrName;

    @Option(names = {"-P", "--param"}, split = "=", arity = "1..2", mapFallbackValue = "",
            description = "Key=value parameter. Repeatable: -P key1=val1 -P key2=val2")
    private Map<String, String> params;

    @Option(names = {"-w", "--wait"}, description = "Poll until terminal status")
    private boolean wait;

    @Option(names = {"--json"}, description = "Output raw JSON")
    private boolean jsonOutput;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    public JobsRunCommand() {
        this(HttpClient.newHttpClient());
    }

    public JobsRunCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();
        boolean isNumeric = jobIdOrName.matches("\\d+");
        String triggerUrl;

        if (isNumeric) {
            triggerUrl = serverUrl + "/api/jobs/" + jobIdOrName + "/run";
        } else {
            triggerUrl = serverUrl + "/api/jobs/name/" + jobIdOrName + "/run";
        }

        // Build request body with parameters if provided
        String requestBody = "{}";
        if (params != null && !params.isEmpty()) {
            requestBody = objectMapper.writeValueAsString(Map.of("parameters", params));
        }

        HttpRequest triggerRequest = HttpRequest.newBuilder()
                .uri(URI.create(triggerUrl))
                .header("Authorization", "Bearer " + effectiveToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> triggerResponse = httpClient.send(triggerRequest, HttpResponse.BodyHandlers.ofString());

        if (triggerResponse.statusCode() == 401) {
            System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
            return 1;
        }

        JsonNode root = objectMapper.readTree(triggerResponse.body());
        if (!root.path("success").asBoolean(true)) {
            System.err.println("Error: " + root.path("error").asText("Unknown error"));
            return 1;
        }

        long runId = root.path("data").path("runId").asLong();

        if (jsonOutput) {
            System.out.println(objectMapper.writeValueAsString(root.path("data")));
        } else {
            System.out.println("Run " + runId + " triggered.");
        }

        if (!wait) return 0;

        // Poll until terminal status
        String pollUrl = serverUrl + "/api/runs/" + runId;
        while (true) {
            Thread.sleep(2000);

            HttpRequest pollRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .header("Authorization", "Bearer " + effectiveToken)
                    .GET()
                    .build();

            HttpResponse<String> pollResponse = httpClient.send(pollRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode pollRoot = objectMapper.readTree(pollResponse.body());
            String status = pollRoot.path("data").path("status").asText("");

            if (status.equals("SUCCESS")) {
                System.out.println("Run " + runId + " completed: SUCCESS");
                return 0;
            } else if (status.equals("FAILED") || status.equals("PARTIAL")) {
                System.out.println("Run " + runId + " completed: " + status);
                return 1;
            } else if (status.equals("CANCELLED")) {
                System.out.println("Run " + runId + " cancelled.");
                return 2;
            }
        }
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
