package com.savoris.controller;

import com.savoris.dto.response.AuditLogResponse;
import com.savoris.entity.AuditLogEntity;
import com.savoris.repository.AuditLogRepository;
import com.savoris.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "Nhật ký hoạt động hệ thống (ADMIN only)")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tra cứu nhật ký hoạt động")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AuditLogResponse>> search(
            @RequestParam(required = false) Integer branchId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<AuditLogEntity> logs = auditLogRepository.search(
            branchId, action, entityType,
            PageRequest.of(page, Math.min(size, 200))
        );
        return ResponseEntity.ok(logs.getContent().stream().map(AuditLogResponse::from).toList());
    }
}
