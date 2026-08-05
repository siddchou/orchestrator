package com.novakai.orch.cli.runs;

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

@Command(name = "list", description = {"List job run history"})
public class RunsListCommand implements Callable<Integer> {

    @Option(names = {"-j", "--job"}, description = "Filter by job ID")
    private Long jobId;

    @Option(names = {"--status"}, description = "Filter by status: SUCCESS, FAILED, PARTIAL, CANCELLED")
    private String status;

    @Option(names = {"--from"}, description = "Start date (YYYY-MM-DD)")
    private String fromDate;

    @Option(names = {"--to"}, description = "End date (YYYY-MM-DD)")
    private String toDate;

    @Option(names = {"-p", "--page"}, defaultValue = "0", description = "Page number (0-based)")
    private int page;

    @Option(names = {"-s", "--size"}, defaultValue = "20", description = "Page size")
    private int size;

    @Option(names = {"--json"}, description = "Output raw JSON")
    private boolean jsonOutput;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    public RunsListCommand() {
        this(HttpClient.newHttpClient());
    }

    public RunsListCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();
        StringBuilder uri = new StringBuilder(serverUrl + "/api/runs?page=" + page + "&size=" + size);
        if (jobId != null) uri.append("&jobId=").append(jobId);
        if (status != null && !status.isEmpty()) uri.append("&status=").append(status);
        if (fromDate != null && !fromDate.isEmpty()) uri.append("&from=").append(fromDate);
        if (toDate != null && !toDate.isEmpty()) uri.append("&to=").append(toDate);

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
                System.out.println("No runs found.");
            } else {
                System.out.printf("%-8s %-6s %-24s %-10s %s%n", "RUN_ID", "JOB_ID", "JOB_NAME", "STATUS", "START_TIME");
                System.out.println("-".repeat(70));

                for (JsonNode run : content) {
                    long runId = run.path("runId").asLong();
                    long jId = run.path("jobId").asLong();
                    String name = truncate(run.path("jobName").asText(""), 24);
                    String st = run.path("status").asText("");
                    String startTime = run.has("startTime") ? run.path("startTime").asText("") : "";

                    System.out.printf("%-8d %-6d %-24s %-10s %s%n", runId, jId, name, st, startTime);
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
