package com.savoris.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.savoris.entity.AuditLogEntity;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLogResponse {
    private Integer id;
    private Integer staffId;
    private Integer branchId;
    private String action;
    private String entityType;
    private Integer entityId;
    private String oldValue;
    private String newValue;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLogEntity e) {
        return AuditLogResponse.builder()
            .id(e.getId())
            .staffId(e.getStaffId())
            .branchId(e.getBranchId())
            .action(e.getAction())
            .entityType(e.getEntityType())
            .entityId(e.getEntityId())
            .oldValue(e.getOldValue())
            .newValue(e.getNewValue())
            .createdAt(e.getCreatedAt())
            .build();
    }
}
