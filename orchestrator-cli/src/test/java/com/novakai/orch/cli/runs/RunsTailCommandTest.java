package com.novakai.orch.cli.runs;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RunsTailCommandTest {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class);
    }

    @Test
    void tail_non_follow_fetches_log() throws Exception {
        String mockResponse = "line1\nline2";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(), any())).thenAnswer(i -> raw);

        RunsTailCommand cmd = new RunsTailCommand(httpClient);
        setField(cmd, "runId", 42L);
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");
        setField(cmd, "follow", false);

        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        try {
            System.setOut(new PrintStream(outCapture));
            int exitCode = cmd.call();
            assertEquals(0, exitCode);
            String output = outCapture.toString();
            assertTrue(output.contains("line1"));
            assertTrue(output.contains("line2"));
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
