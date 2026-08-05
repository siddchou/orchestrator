package com.novakai.orch.cli.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

@Command(name = "list", description = {"List notification subscriptions"})
public class NotificationsListCommand implements Callable<Integer> {

    @Option(names = {"-j", "--job"}, description = "Filter by job ID")
    private Long jobId;

    @Option(names = {"--json"}, description = "Output raw JSON")
    private boolean jsonOutput;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    public NotificationsListCommand() {
        this(HttpClient.newHttpClient());
    }

    public NotificationsListCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();
        String url;

        if (jobId != null) {
            url = serverUrl + "/api/notifications/subscriptions/job/" + jobId;
        } else {
            url = serverUrl + "/api/notifications/subscriptions";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + effectiveToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            System.err.println("ADMIN role required to list notification subscriptions.");
            return 1;
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("success").asBoolean(true)) {
            System.err.println("Error: " + root.path("error").asText("Unknown error"));
            return 1;
        }

        JsonNode data = root.path("data");
        // Handle both array and wrapped response formats
        if (!data.isArray()) {
            data = data.path("content");
        }

        if (jsonOutput) {
            System.out.println(objectMapper.writeValueAsString(data));
        } else {
            if (data.size() == 0) {
                System.out.println("No subscriptions found.");
            } else {
                System.out.printf("%-6s %-12s %-15s %-10s %s%n", "ID", "JOB_ID", "CHANNEL", "EVENTS", "ACTIVE");
                System.out.println("-".repeat(60));

                for (JsonNode sub : data) {
                    long id = sub.path("id").asLong();
                    long jId = sub.path("jobId").asLong();
                    String channel = sub.path("channelType").asText("");
                    String events = sub.path("events").isArray() ?
                            objectMapper.writeValueAsString(sub.path("events")) : "";
                    boolean active = sub.path("active").asBoolean(true);

                    System.out.printf("%-6d %-12d %-15s %-10b %s%n", id, jId, channel, events, active);
                }
            }
        }

        return 0;
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
