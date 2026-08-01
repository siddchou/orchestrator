package com.novakai.orchestrator.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novakai.orchestrator.api.dto.NotificationSubscriptionRequest;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.notification.spi.NotificationChannelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSubscriptionRepository subscriptionRepo;
    private final NotificationChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> VALID_EVENT_NAMES = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "PARTIAL", "CANCELLED", "SKIPPED"
    );

    public List<NotificationSubscription> listAll() {
        return subscriptionRepo.findAll();
    }

    public NotificationSubscription getById(Long id) {
        return subscriptionRepo.findById(id).orElse(null);
    }

    public List<NotificationSubscription> getByJobId(Long jobId) {
        return subscriptionRepo.findByJobIdAndActiveTrue(jobId);
    }

    @Transactional
    public NotificationSubscription create(NotificationSubscriptionRequest request) {
        validate(request);
        String configJson = toJson(request.config());
        NotificationSubscription sub = NotificationSubscription.builder()
                .jobId(request.jobId())
                .channelType(request.channelType())
                .events(String.join(",", request.events()))
                .configJson(configJson)
                .active(true)
                .build();
        return subscriptionRepo.save(sub);
    }

    @Transactional
    public NotificationSubscription update(Long id, NotificationSubscriptionRequest request) {
        validate(request);
        NotificationSubscription sub = getById(id);
        if (sub == null) return null;

        String configJson = toJson(request.config());
        sub.setChannelType(request.channelType());
        sub.setEvents(String.join(",", request.events()));
        sub.setConfigJson(configJson);
        return subscriptionRepo.save(sub);
    }

    public void delete(Long id) {
        if (!subscriptionRepo.existsById(id)) throw new IllegalArgumentException("Subscription not found: " + id);
        subscriptionRepo.deleteById(id);
    }

    @Transactional
    public NotificationSubscription toggle(Long id) {
        NotificationSubscription sub = getById(id);
        if (sub == null) return null;
        sub.setActive(!sub.isActive());
        return subscriptionRepo.save(sub);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /** Validates channel type is registered and event names are valid RunStatus values. */
    public void validate(NotificationSubscriptionRequest request) {
        if (request.channelType() == null || request.channelType().isBlank()) {
            throw new IllegalArgumentException("channelType must not be blank");
        }
        if (!channelRegistry.get(request.channelType()).isPresent()) {
            throw new IllegalArgumentException(
                    "No registered channel for type: " + request.channelType());
        }
        if (request.events() == null || request.events().isEmpty()) {
            throw new IllegalArgumentException("events list must not be empty");
        }
        for (String event : request.events()) {
            if (!VALID_EVENT_NAMES.contains(event.toUpperCase())) {
                throw new IllegalArgumentException(
                        "Invalid event name: '" + event + "'. Must be one of: " + VALID_EVENT_NAMES);
            }
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid config JSON: " + e.getMessage());
        }
    }
}
