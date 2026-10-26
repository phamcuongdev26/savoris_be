package com.savoris.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
            .oldValue(parse(oldValue))
            .newValue(parse(newValue))
            .build();
        repo.save(entry);
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
