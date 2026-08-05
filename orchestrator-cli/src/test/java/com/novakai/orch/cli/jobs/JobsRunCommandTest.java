package com.novakai.orch.cli.jobs;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobsRunCommandTest {

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(HttpClient.class);
    }

    @Test
    void run_by_id_triggers_correctly() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"runId\":42}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(i -> raw);

        JobsRunCommand cmd = new JobsRunCommand(httpClient);
        setField(cmd, "jobIdOrName", "5");
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");

        int exitCode = cmd.call();

        assertEquals(0, exitCode);

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertTrue(reqCaptor.getValue().uri().toString().contains("/api/jobs/5/run"));
    }

    @Test
    void run_by_name_routes_to_name_endpoint() throws Exception {
        String mockResponse = "{\"success\":true,\"data\":{\"runId\":43}}";

        HttpResponse<Object> raw = Mockito.mock(HttpResponse.class);
        when(raw.body()).thenReturn(mockResponse);
        when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(i -> raw);

        JobsRunCommand cmd = new JobsRunCommand(httpClient);
        setField(cmd, "jobIdOrName", "DailyETL");
        setField(cmd, "serverUrl", "http://localhost:8080");
        setField(cmd, "token", "test-token");

        int exitCode = cmd.call();

        assertEquals(0, exitCode);

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCaptor.capture(), any());
        assertTrue(reqCaptor.getValue().uri().toString().contains("/api/jobs/name/DailyETL/run"));
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
