package com.novakai.orchestrator.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.NotificationDeliveryLogResponse;
import com.novakai.orchestrator.api.dto.NotificationSubscriptionRequest;
import com.novakai.orchestrator.api.dto.NotificationSubscriptionResponse;
import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.repository.NotificationDeliveryLogRepository;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.notification.spi.ChannelConfigSchema;
import com.novakai.orchestrator.notification.spi.NotificationChannelRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationSubscriptionRepository subscriptionRepo;
    private final NotificationDeliveryLogRepository deliveryLogRepo;
    private final NotificationChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // Subscriptions CRUD
    // ------------------------------------------------------------------

    @GetMapping("/subscriptions")
    public ApiResponse<List<NotificationSubscriptionResponse>> listSubscriptions() {
        return ApiResponse.success(subscriptionRepo.findAll().stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/subscriptions/{id}")
    public ApiResponse<NotificationSubscriptionResponse> getSubscription(@PathVariable Long id) {
        NotificationSubscription sub = subscriptionRepo.findById(id).orElse(null);
        if (sub == null) return ApiResponse.error("Subscription not found: " + id);
        return ApiResponse.success(toResponse(sub));
    }

    @GetMapping("/subscriptions/job/{jobId}")
    public ApiResponse<List<NotificationSubscriptionResponse>> getSubscriptionsForJob(@PathVariable Long jobId) {
        return ApiResponse.success(subscriptionRepo.findByJobIdAndActiveTrue(jobId).stream()
            .map(this::toResponse)
            .toList());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NotificationSubscriptionResponse> createSubscription(
            @Valid @RequestBody NotificationSubscriptionRequest request) {
        try {
            String configJson = objectMapper.writeValueAsString(request.config());
            NotificationSubscription sub = NotificationSubscription.builder()
                .jobId(request.jobId())
                .channelType(request.channelType())
                .events(String.join(",", request.events()))
                .configJson(configJson)
                .active(true)
                .build();
            sub = subscriptionRepo.save(sub);
            return ApiResponse.success(toResponse(sub));
        } catch (JsonProcessingException e) {
            return ApiResponse.error("Invalid config JSON: " + e.getMessage());
        }
    }

    @PutMapping("/subscriptions/{id}")
    public ApiResponse<NotificationSubscriptionResponse> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody NotificationSubscriptionRequest request) {
        NotificationSubscription sub = subscriptionRepo.findById(id).orElse(null);
        if (sub == null) return ApiResponse.error("Subscription not found: " + id);

        try {
            String configJson = objectMapper.writeValueAsString(request.config());
            sub.setChannelType(request.channelType());
            sub.setEvents(String.join(",", request.events()));
            sub.setConfigJson(configJson);
            sub = subscriptionRepo.save(sub);
            return ApiResponse.success(toResponse(sub));
        } catch (JsonProcessingException e) {
            return ApiResponse.error("Invalid config JSON: " + e.getMessage());
        }
    }

    @DeleteMapping("/subscriptions/{id}")
    public ApiResponse<Void> deleteSubscription(@PathVariable Long id) {
        if (!subscriptionRepo.existsById(id)) {
            return ApiResponse.error("Subscription not found: " + id);
        }
        subscriptionRepo.deleteById(id);
        return ApiResponse.success();
    }

    @PatchMapping("/subscriptions/{id}/toggle")
    public ApiResponse<NotificationSubscriptionResponse> toggleSubscription(@PathVariable Long id) {
        NotificationSubscription sub = subscriptionRepo.findById(id).orElse(null);
        if (sub == null) return ApiResponse.error("Subscription not found: " + id);
        sub.setActive(!sub.isActive());
        sub = subscriptionRepo.save(sub);
        return ApiResponse.success(toResponse(sub));
    }

    // ------------------------------------------------------------------
    // Channel schemas (for dynamic form rendering in UI)
    // ------------------------------------------------------------------

    @GetMapping("/channels")
    public ApiResponse<List<ChannelConfigSchema>> listChannelSchemas() {
        return ApiResponse.success(channelRegistry.listAll());
    }

    // ------------------------------------------------------------------
    // Delivery log
    // ------------------------------------------------------------------

    @GetMapping("/delivery-log")
    public ApiResponse<List<NotificationDeliveryLogResponse>> getDeliveryLog(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) Long subscriptionId) {
        List<NotificationDeliveryLog> logs;
        if (runId != null) {
            logs = deliveryLogRepo.findByRunIdOrderByCreatedAtDesc(runId);
        } else if (subscriptionId != null) {
            logs = deliveryLogRepo.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
        } else {
            logs = deliveryLogRepo.findAll();
        }
        return ApiResponse.success(logs.stream().map(this::toLogResponse).toList());
    }

    // ------------------------------------------------------------------
    // Mappers
    // ------------------------------------------------------------------

    private NotificationSubscriptionResponse toResponse(NotificationSubscription sub) {
        Map<String, Object> config = Collections.emptyMap();
        if (sub.getConfigJson() != null && !sub.getConfigJson().isBlank()) {
            try {
                config = objectMapper.readValue(sub.getConfigJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                // ignore parse errors; return empty config
            }
        }
        return new NotificationSubscriptionResponse(
            sub.getId(), sub.getJobId(), sub.getChannelType(),
            sub.getEvents(), config, sub.isActive(), sub.getCreatedAt()
        );
    }

    private NotificationDeliveryLogResponse toLogResponse(NotificationDeliveryLog log) {
        return new NotificationDeliveryLogResponse(
            log.getId(), log.getSubscriptionId(), log.getRunId(),
            log.getChannelType(), log.getStatus(), log.getAttemptCount(),
            log.getErrorMessage(), log.getCreatedAt(), log.getSentAt()
        );
    }
}
