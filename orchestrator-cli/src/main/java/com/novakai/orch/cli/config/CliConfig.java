package com.novakai.orch.cli.config;

import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class CliConfig {

    @CommandLine.Option(names = {"-s", "--server"}, description = "API base URL (env: ORCHESTRATOR_URL)",
            defaultValue = "${env:ORCHESTRATOR_URL:http://localhost:8080}")
    private String serverUrl;

    @CommandLine.Option(names = {"--token"}, description = "JWT token (env: ORCHESTRATOR_TOKEN)")
    private String token;

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * Get the effective JWT token. Checks env var, then mixin field, then file cache.
     */
    public String getToken() throws java.io.IOException {
        // 1. Environment variable
        String envToken = System.getenv("ORCHESTRATOR_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken;

        // 2. Command-line option
        if (token != null && !token.isBlank()) return token;

        // 3. File cache
        Path tokenFile = tokenFilePath();
        if (Files.exists(tokenFile)) {
            String cached = Files.readString(tokenFile).trim();
            if (!cached.isEmpty()) return cached;
        }

        throw new IllegalStateException("No authentication token found. Run 'orch login' first, " +
                "or set ORCHESTRATOR_TOKEN environment variable.");
    }

    /**
     * Save a token to the file cache.
     */
    public void saveToken(String t) throws java.io.IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".orchestrator");
        Files.createDirectories(dir);
        Path tokenFile = dir.resolve("token");
        Files.writeString(tokenFile, Objects.requireNonNull(t));
        // Try to set restrictive permissions (Unix only)
        try { java.io.File f = tokenFile.toFile(); f.setReadable(false, true); f.setWritable(false, true); } catch (Exception ignored) {}
    }

    private Path tokenFilePath() {
        return Paths.get(System.getProperty("user.home"), ".orchestrator", "token");
    }
}
