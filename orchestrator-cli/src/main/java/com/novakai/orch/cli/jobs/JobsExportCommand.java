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

@Command(name = "export", description = {"Export a job definition to file"})
public class JobsExportCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Job ID")
    private Long jobId;

    @Option(names = {"-f", "--format"}, defaultValue = "json", description = "Export format: json or yaml")
    private String format;

    @Option(names = {"-o", "--output"}, description = "File path to write export (default: stdout)")
    private Path outputPath;

    @Option(names = {"-S", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    private HttpClient httpClient;

    public JobsExportCommand() {
        this(HttpClient.newHttpClient());
    }

    public JobsExportCommand(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Integer call() throws Exception {
        String effectiveToken = getToken();
        String url = serverUrl + "/api/jobs/" + jobId + "/export?format=" + format;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + effectiveToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            System.err.println("Token expired or invalid. Run 'orch login' to get a new token.");
            return 1;
        }

        String body = response.body();

        if (outputPath != null) {
            java.nio.file.Files.writeString(outputPath, body);
            System.out.println("Exported job " + jobId + " to " + outputPath);
        } else {
            System.out.print(body);
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
