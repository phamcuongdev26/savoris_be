package com.savoris.service;

import com.savoris.entity.AuditLogEntity;
import com.savoris.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Thin async wrapper around AuditLogRepository.
 * Call log() from any service/controller without blocking the request.
 */
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository repo;

    @Async
    public void log(Integer staffId, Integer branchId,
                    String action, String entityType, Integer entityId,
                    String oldValue, String newValue) {
        AuditLogEntity entry = AuditLogEntity.builder()
            .staffId(staffId)
            .branchId(branchId)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .oldValue(oldValue)
            .newValue(newValue)
            .build();
        repo.save(entry);
    }
}
