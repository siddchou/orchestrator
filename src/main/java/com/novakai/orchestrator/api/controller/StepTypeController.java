package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepExecutorRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Step Types", description = "Registered step type schemas")
@RequiredArgsConstructor
public class StepTypeController {

    private final StepExecutorRegistry registry;

    @Operation(summary = "List all registered step types with config schemas")
    @GetMapping("/step-types")
    public ApiResponse<List<StepConfigSchema>> listStepTypes() {
        return ApiResponse.success(registry.listAll());
    }
}
