package com.savoris.repository;

import com.savoris.entity.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Integer> {
    List<PushSubscriptionEntity> findByOrderId(Integer orderId);
    Optional<PushSubscriptionEntity> findByOrderIdAndEndpoint(Integer orderId, String endpoint);
}
