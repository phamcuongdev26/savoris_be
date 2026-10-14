package com.savoris.repository;

import com.savoris.entity.PurchaseOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItemEntity, Integer> {
    List<PurchaseOrderItemEntity> findByPurchaseOrderId(Integer purchaseOrderId);
}
