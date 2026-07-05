package com.novakai.orchestrator;

// @author Siddhant Choudhary

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import jakarta.annotation.PreDestroy;

@SpringBootApplication
@Slf4j
public class OrchestratorApplication {

    @Autowired
    private Environment env;

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Orchestrator started on port {} with active profiles: {}",
                env.getProperty("server.port", "N/A"),
                env.getActiveProfiles().length > 0 ? (Object) env.getActiveProfiles() : "(default)");
    }

    @PreDestroy
    public void onApplicationShutdown() {
        log.info("Orchestrator shutting down - waiting for in-progress jobs to complete");
    }
}
