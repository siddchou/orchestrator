package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
public class JsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("Failed to parse JSON config: {}", e.getMessage());
            throw new RuntimeException("Failed to parse JSON config: " + e.getMessage(), e);
        }
    }
}
