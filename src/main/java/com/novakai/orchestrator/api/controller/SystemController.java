package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.EnvVarRequest;
import com.novakai.orchestrator.api.dto.EnvVarResponse;
import com.novakai.orchestrator.api.mapper.JobDefinitionMapper;
import com.novakai.orchestrator.domain.entity.JobEnvVar;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import org.springframework.scheduling.support.CronExpression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SystemController {

    private final JobEnvVarRepository envVarRepo;
    private final JobDefinitionMapper mapper;

    @GetMapping("/env-vars/global")
    public ApiResponse<List<EnvVarResponse>> listGlobal() {
        return ApiResponse.success(envVarRepo.findByIsGlobal("Y").stream()
                .map(mapper::toEnvVarResponse).toList());
    }

    @PostMapping("/env-vars/global")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnvVarResponse> addGlobal(@Valid @RequestBody EnvVarRequest req) {
        JobEnvVar envVar = JobEnvVar.builder()
                .varName(req.key())
                .varValue(req.value())
                .isGlobal("Y")
                .build();
        envVar = envVarRepo.save(envVar);
        return ApiResponse.success(mapper.toEnvVarResponse(envVar));
    }

    @DeleteMapping("/env-vars/global/{envId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobal(@PathVariable Long envId) {
        JobEnvVar envVar = envVarRepo.findById(envId)
                .orElseThrow(() -> new EntityNotFoundException("Global env var not found: " + envId));
        envVarRepo.delete(envVar);
    }

    @GetMapping("/system/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("workingDirAccessible",
                Files.isDirectory(Path.of(System.getProperty("user.dir"))));
        return ApiResponse.success(status);
    }

    @GetMapping("/system/env-validate")
    public ApiResponse<Map<String, String>> validateEnv(
            @RequestParam String javaHome,
            @RequestParam String workingDir) {
        Map<String, String> results = new LinkedHashMap<>();
        results.put("javaHome", Files.isDirectory(Path.of(javaHome)) ? "OK" : "NOT_FOUND");
        results.put("javaBin", Files.isExecutable(Path.of(javaHome, "bin", "java")) ? "OK" : "NOT_EXECUTABLE");
        results.put("workingDir", Files.isDirectory(Path.of(workingDir)) ? "OK" : "NOT_FOUND");
        results.put("workingDirWritable", Files.isWritable(Path.of(workingDir)) ? "OK" : "NOT_WRITABLE");
        return ApiResponse.success(results);
    }

    @GetMapping("/system/cron-validate")
    public ApiResponse<Map<String, String>> validateCron(@RequestParam String expression) {
        try {
            CronExpression cron = CronExpression.parse(expression);
            Instant now = Instant.now();
            Instant next1 = cron.next(now);
            Instant next2 = cron.next(next1);
            Instant next3 = cron.next(next2);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("valid", "true");
            result.put("next1", next1 != null ? next1.toString() : "none");
            result.put("next2", next2 != null ? next2.toString() : "none");
            result.put("next3", next3 != null ? next3.toString() : "none");
            return ApiResponse.success(result);
        } catch (IllegalArgumentException ex) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("valid", "false");
            result.put("error", ex.getMessage());
            return ApiResponse.success(result);
        }
    }
}