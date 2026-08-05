package com.novakai.orchestrator.engine.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PrometheusConfig {

    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(
                io.micrometer.prometheus.PrometheusConfig.DEFAULT);
    }
}
