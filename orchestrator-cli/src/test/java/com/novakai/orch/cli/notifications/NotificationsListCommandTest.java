package com.novakai.orch.cli.notifications;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class NotificationsListCommandTest {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class);
    }

    @Test
    void list_for_job_renders_table() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":[{\"id\":1,\"jobId\":5,\"channelType\":\"MAIL\",\"events\":[\"RUN_COMPLETE\"],\"active\":true}]}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        NotificationsListCommand cmd = new NotificationsListCommand(httpClient);
        setField(cmd, "jobId", 5L);
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
            assertTrue(output.contains("MAIL"));
        } finally {
            System.setOut(savedOut);
        }
    }

    @Test
    void list_forbidden_shows_error() throws Exception {
        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.statusCode()).thenReturn(403);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        NotificationsListCommand cmd = new NotificationsListCommand(httpClient);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream savedErr = System.err;
        try {
            System.setErr(new PrintStream(errCapture));
            int exitCode = cmd.call();
            assertEquals(1, exitCode);
            String error = errCapture.toString();
            assertTrue(error.contains("ADMIN"));
        } finally {
            System.setErr(savedErr);
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
