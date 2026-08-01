package com.novakai.orchestrator.notification.repository;

import com.novakai.orchestrator.notification.entity.NotificationDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {
    List<NotificationDeliveryLog> findByRunIdOrderByCreatedAtDesc(Long runId);
    List<NotificationDeliveryLog> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}
