package com.novakai.orch.cli.runs;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.util.concurrent.Callable;

@Command(name = "tail", description = {"Stream live logs for a running job via SSE"})
public class RunsTailCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Run ID to tail")
    private Long runId;

    @Option(names = {"-f", "--follow"}, defaultValue = "true",
            description = "Keep connection open and stream logs (default: true)")
    private boolean follow;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private HttpClient httpClient;

    public RunsTailCommand() {
        this(HttpClient.newHttpClient());
    }

    public RunsTailCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();

        if (follow) {
            return streamLogs(effectiveToken);
        } else {
            return fetchAccumulatedLog(effectiveToken);
        }
    }

    int streamLogs(String token) throws IOException, InterruptedException {
        String url = serverUrl + "/api/runs/" + runId + "/log-stream";
        int retries = 0;
        int maxRetries = 3;

        while (retries <= maxRetries) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 401) {
                    System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
                    return 1;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.startsWith("done:")) {
                                System.out.println("[" + LocalTime.now().toString() + "] " + data);
                                return 0;
                            }
                            System.out.println("[" + LocalTime.now().toString() + "] " + data);
                        } else if (line.isEmpty()) {
                            // SSE blank line separator, skip
                        }
                    }
                }
                // Stream ended without done event
                System.err.println("[connection closed]");
                return 0;

            } catch (IOException | InterruptedException e) {
                System.err.println("[connection lost]");
                retries++;
                if (retries <= maxRetries) {
                    long backoff = (long) Math.pow(2, retries - 1) * 1000;
                    Thread.sleep(backoff);
                } else {
                    return 3;
                }
            }
        }

        return 3;
    }

    int fetchAccumulatedLog(String token) throws IOException, InterruptedException {
        String url = serverUrl + "/api/runs/" + runId + "/steps/1/log";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
            return 1;
        }

        String body = response.body();
        if (body != null && !body.isBlank()) {
            String[] lines = body.split("\n");
            for (String line : lines) {
                System.out.println("[" + LocalTime.now().toString() + "] " + line);
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
