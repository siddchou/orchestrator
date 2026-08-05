package com.novakai.orch.cli.jobs;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JobsListCommandTest {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class);
    }

    @Test
    void list_default_page_shows_table() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"content\":[{\"jobId\":1,\"jobName\":\"DailyETL\",\"enabled\":\"Y\",\"steps\":[],\"schedule\":{\"cronExpression\":\"0 2 * * *\"}}],\"totalPages\":3}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        JobsListCommand cmd = new JobsListCommand(httpClient);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");

        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        try {
            System.setOut(new PrintStream(outCapture));
            int exitCode = cmd.call();
            assertEquals(0, exitCode);
            String output = outCapture.toString();
            assertTrue(output.contains("ID"));
            assertTrue(output.contains("NAME"));
            assertTrue(output.contains("DailyETL"));
            assertTrue(output.contains("Page 1/3"));
        } finally {
            System.setOut(savedOut);
        }
    }

    @Test
    void list_empty_result() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"content\":[],\"totalPages\":0}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        JobsListCommand cmd = new JobsListCommand(httpClient);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");

        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        try {
            System.setOut(new PrintStream(outCapture));
            int exitCode = cmd.call();
            assertEquals(0, exitCode);
            assertTrue(outCapture.toString().contains("No jobs found."));
        } finally {
            System.setOut(savedOut);
        }
    }

    @Test
    void list_json_output() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"content\":[{\"jobId\":1}],\"totalPages\":1}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        JobsListCommand cmd = new JobsListCommand(httpClient);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");
        setField(cmd, "jsonOutput", true);

        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        try {
            System.setOut(new PrintStream(outCapture));
            int exitCode = cmd.call();
            assertEquals(0, exitCode);
            assertTrue(outCapture.toString().contains("jobId"));
        } finally {
            System.setOut(savedOut);
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
