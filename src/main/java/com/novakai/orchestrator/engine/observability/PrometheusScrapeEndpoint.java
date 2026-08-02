package com.novakai.orchestrator.engine.observability;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrometheusScrapeEndpoint {

    private final PrometheusMeterRegistry prometheusRegistry;

    public PrometheusScrapeEndpoint(PrometheusMeterRegistry prometheusRegistry) {
        this.prometheusRegistry = prometheusRegistry;
    }

    @GetMapping("/actuator/prometheus")
    public String scrape() {
        return prometheusRegistry.scrape();
    }
}
