package com.savoris.repository;

import com.savoris.entity.PurchaseOrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, Integer> {
    List<PurchaseOrderEntity> findByBranchIdOrderByCreatedAtDesc(Integer branchId);

    // Receiving the same delivery twice would double the stock: status changes run one at a time
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrderEntity p where p.id = :id")
    Optional<PurchaseOrderEntity> findByIdForUpdate(@Param("id") Integer id);
}
