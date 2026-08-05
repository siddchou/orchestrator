package com.novakai.orch.cli.jobs;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobsExportImportTest {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class);
    }

    @Test
    void export_json_to_file() throws Exception {
        String mockResponse = "{\"jobName\":\"DailyETL\",\"steps\":[]}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        Path tempFile = Files.createTempFile("job-export", ".json");
        try {
            JobsExportCommand cmd = new JobsExportCommand(httpClient);
            setField(cmd, "jobId", 1L);
            setField(cmd, "serverUrl", "http://localhost:8080");
            setField(cmd, "token", "test-token");
            setField(cmd, "outputPath", tempFile);

            int exitCode = cmd.call();

            assertEquals(0, exitCode);
            String content = Files.readString(tempFile);
            assertTrue(content.contains("DailyETL"), "Expected file to contain DailyETL but got: " + content);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertTrue(reqCaptor.getValue().uri().toString().contains("/api/jobs/1/export"));
    }

    @Test
    void export_yaml_format() throws Exception {
        String mockResponse = "jobName: DailyETL";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        JobsExportCommand cmd = new JobsExportCommand(httpClient);
        setField(cmd, "jobId", 1L);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");
        setField(cmd, "format", "yaml");

        int exitCode = cmd.call();

        assertEquals(0, exitCode);

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertTrue(reqCaptor.getValue().uri().toString().contains("format=yaml"));
    }

    @Test
    void import_json_file() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"jobId\":1}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(raw.statusCode()).thenReturn(201);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        Path tempFile = Files.createTempFile("job-import", ".json");
        try {
            Files.writeString(tempFile, "{\"jobName\":\"ImportedJob\",\"steps\":[]}");

            JobsImportCommand cmd = new JobsImportCommand(httpClient);
            setField(cmd, "file", tempFile);
            setField(cmd, "serverUrl", "http://localhost:8080");
            setField(cmd, "token", "test-token");

            int exitCode = cmd.call();

            assertEquals(0, exitCode);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        HttpRequest capturedReq = reqCaptor.getValue();
        assertEquals("application/json", capturedReq.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void import_validation_error() throws Exception {
        String mockResponse = "{\"success\":false,\"error\":\"Import validation failed: circular dependency\"}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(raw.statusCode()).thenReturn(400);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        Path tempFile = Files.createTempFile("job-import", ".json");
        try {
            Files.writeString(tempFile, "{\"jobName\":\"BadJob\"}");

            JobsImportCommand cmd = new JobsImportCommand(httpClient);
            setField(cmd, "file", tempFile);
            setField(cmd, "serverUrl", "http://localhost:8080");
            setField(cmd, "token", "test-token");

            int exitCode = cmd.call();

            assertEquals(1, exitCode);
        } finally {
            Files.deleteIfExists(tempFile);
        }
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
