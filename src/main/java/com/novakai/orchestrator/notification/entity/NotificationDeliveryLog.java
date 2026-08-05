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
@Table(name = "NOTIFICATION_DELIVERY_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SUBSCRIPTION_ID", nullable = false)
    private Long subscriptionId;

    @Column(nullable = false)
    private Long runId;

    @Column(name = "CHANNEL_TYPE", nullable = false, length = 50)
    private String channelType;

    /** NotificationEvent serialized as JSON */
    @Lob
    @Column(name = "EVENTS_JSON")
    private String eventsJson;

    /** ChannelConfig snapshot at dispatch time */
    @Lob
    @Column(name = "CONFIG_JSON")
    private String configJson;

    /** PENDING, SENT, FAILED */
    @Column(nullable = false)
    private String status;

    @Lob
    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime sentAt;
}
