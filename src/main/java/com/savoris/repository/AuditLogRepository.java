package com.savoris.repository;

import com.savoris.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Integer> {

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(:branchId IS NULL OR a.branchId = :branchId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> search(
        @Param("branchId") Integer branchId,
        @Param("action")   String action,
        @Param("entityType") String entityType,
        Pageable pageable
    );
}
