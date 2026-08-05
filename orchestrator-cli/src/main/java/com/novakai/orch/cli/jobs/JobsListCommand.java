package com.novakai.orch.cli.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "list", description = {"List job definitions"})
public class JobsListCommand implements Callable<Integer> {

    @Option(names = {"-p", "--page"}, defaultValue = "0", description = "Page number (0-based)")
    private int page;

    @Option(names = {"-s", "--size"}, defaultValue = "20", description = "Page size")
    private int size;

    @Option(names = {"-q", "--search"}, description = "Filter by job name substring")
    private String search;

    @Option(names = {"--json"}, description = "Output raw JSON")
    private boolean jsonOutput;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    public JobsListCommand() {
        this(HttpClient.newHttpClient());
    }

    public JobsListCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();
        StringBuilder uri = new StringBuilder(serverUrl + "/api/jobs?page=" + page + "&size=" + size);
        if (search != null && !search.isEmpty()) {
            uri.append("&search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri.toString()))
                .header("Authorization", "Bearer " + effectiveToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
            return 1;
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("success").asBoolean(true)) {
            System.err.println("Error: " + root.path("error").asText("Unknown error"));
            return 1;
        }

        JsonNode data = root.path("data");
        JsonNode content = data.path("content");
        int totalPages = data.path("totalPages").asInt(0);

        if (jsonOutput) {
            System.out.println(objectMapper.writeValueAsString(content));
        } else {
            if (content.size() == 0) {
                System.out.println("No jobs found.");
            } else {
                // Print table header
                System.out.printf("%-6s %-24s %-8s %-7s %s%n", "ID", "NAME", "ENABLED", "STEPS", "SCHEDULE");
                System.out.println("-".repeat(60));

                for (JsonNode job : content) {
                    long jobId = job.path("jobId").asLong();
                    String name = truncate(job.path("jobName").asText(""), 24);
                    String enabled = job.path("enabled").asText("");
                    int steps = job.path("steps").isArray() ? job.path("steps").size() : 0;
                    String schedule = "—";
                    if (job.has("schedule") && !job.path("schedule").isNull()) {
                        JsonNode sched = job.path("schedule");
                        if (sched.has("cronExpression")) {
                            schedule = sched.path("cronExpression").asText("");
                        }
                    }

                    System.out.printf("%-6d %-24s %-8s %-7d %s%n", jobId, name, enabled, steps, schedule);
                }
            }
            if (totalPages > 0) {
                System.out.println("Page " + (page + 1) + "/" + totalPages);
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

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
