package com.savoris.repository;

import com.savoris.entity.OrderItemVoidHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderItemVoidHistoryRepository extends JpaRepository<OrderItemVoidHistoryEntity, Integer> {
    Optional<OrderItemVoidHistoryEntity> findFirstByOrderItemIdAndResolvedAtIsNullOrderByRequestedAtDesc(Integer orderItemId);
}
