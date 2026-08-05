package com.novakai.orchestrator.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "NOTIFICATION_SUBSCRIPTION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    /** Channel type string matching a registered NotificationChannel.getType() */
    @Column(name = "CHANNEL_TYPE", nullable = false, length = 50)
    private String channelType;

    /** Comma-separated event names: SUCCESS, FAILED, PARTIAL */
    @Column(nullable = false, length = 100)
    private String events;

    /** Channel-specific configuration stored as JSON blob */
    @Lob
    @Column(name = "CONFIG_JSON", nullable = false)
    private String configJson;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Parse the comma-separated events string into a list. */
    public java.util.List<String> getEventsList() {
        if (events == null || events.isBlank()) return java.util.Collections.emptyList();
        return java.util.Arrays.stream(events.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
