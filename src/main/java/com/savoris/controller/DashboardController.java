package com.savoris.controller;

import com.savoris.dto.response.ChainDashboardResponse;
import com.savoris.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Chain-wide overview for the owner")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/chain")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tổng quan toàn chuỗi", description = "Đơn đang mở, doanh thu hôm nay và tải bếp của từng chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ChainDashboardResponse> getChainDashboard() {
        return ResponseEntity.ok(dashboardService.getChainDashboard());
    }
}
