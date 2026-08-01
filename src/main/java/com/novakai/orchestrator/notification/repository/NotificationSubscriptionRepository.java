package com.novakai.orchestrator.notification.repository;

import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {
    List<NotificationSubscription> findByJobId(Long jobId);
    List<NotificationSubscription> findByJobIdAndActiveTrue(Long jobId);
}
