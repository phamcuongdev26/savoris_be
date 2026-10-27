package com.savoris.controller;

import com.savoris.dto.response.BranchDashboardResponse;
import com.savoris.dto.response.ChainDashboardResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.DashboardService;
import com.savoris.service.KitchenForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Tổng quan vận hành")
public class DashboardController {

    private final DashboardService dashboardService;
    private final KitchenForecastService kitchenForecastService;

    @GetMapping("/chain")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tổng quan toàn chuỗi", description = "Đơn đang mở, doanh thu hôm nay và tải bếp của từng chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ChainDashboardResponse> getChainDashboard() {
        return ResponseEntity.ok(dashboardService.getChainDashboard());
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Tổng quan chi nhánh", description = "Đơn đang mở, doanh thu, đặt bàn và tải bếp của chi nhánh hôm nay")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BranchDashboardResponse> getBranchDashboard(
            @PathVariable Integer branchId,
            @CurrentUser Integer staffId) {
        return ResponseEntity.ok(dashboardService.getBranchDashboard(staffId, branchId));
    }

    @GetMapping("/branch/{branchId}/kitchen-forecast")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Dự báo tải bếp 2 giờ tới", description = "Dùng tải bếp hiện tại và lịch sử bốn tuần cùng thứ; sampleData=true gửi kịch bản giả lập qua AI để kiểm thử; chỉ manager/admin của chi nhánh.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<KitchenForecastResponse> getKitchenForecast(
            @PathVariable Integer branchId, @CurrentUser Integer staffId,
            @RequestParam(defaultValue = "false") boolean sampleData) {
        return ResponseEntity.ok(kitchenForecastService.forecast(staffId, branchId, sampleData));
    }
}
