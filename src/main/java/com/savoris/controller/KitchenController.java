package com.savoris.controller;

import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.dto.response.KitchenTicketResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.KitchenForecastService;
import com.savoris.service.KitchenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kitchen")
@RequiredArgsConstructor
@Tag(name = "Kitchen", description = "Kitchen display (KDS) and peak-hour load")
public class KitchenController {

    private final KitchenService kitchenService;
    private final KitchenForecastService kitchenForecastService;

    @GetMapping("/branches/{branchId}/insight")
    @Operation(summary = "AI Kitchen Insight", description = "AI tóm tắt tải bếp hiện tại, món đang chờ nhiều nhất và xu hướng hai giờ tới.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<KitchenForecastResponse> getInsight(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(kitchenForecastService.forecast(staffId, branchId));
    }

    @GetMapping("/branches/{branchId}/queue")
    @Operation(summary = "Hàng đợi bếp", description = "Các món chờ nấu, đang nấu, đã xong chờ bưng; món gọi trước đứng trước")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<List<KitchenTicketResponse>> getQueue(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(kitchenService.getQueue(staffId, branchId));
    }

    @GetMapping("/branches/{branchId}/void-queue")
    @Operation(summary = "Danh sách yêu cầu hủy món chờ bếp xử lý")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<List<KitchenTicketResponse>> getVoidQueue(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(kitchenService.getVoidQueue(staffId, branchId));
    }

    @GetMapping("/branches/{branchId}/load")
    @Operation(summary = "Tải bếp", description = "Số phần đang chờ/nấu, % công suất, thời gian chờ ước tính")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<BranchLoadResponse> getLoad(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(kitchenService.getLoad(staffId, branchId));
    }

    @PutMapping("/items/{itemId}/start")
    @Operation(summary = "Nhận nấu món", description = "PENDING → COOKING. Trả 409 nếu người khác đã nhận")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<KitchenTicketResponse> startCooking(@CurrentUser Integer staffId, @PathVariable Integer itemId) {
        return ResponseEntity.ok(kitchenService.startCooking(staffId, itemId));
    }

    @PutMapping("/items/{itemId}/ready")
    @Operation(summary = "Món đã xong", description = "COOKING → READY")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public ResponseEntity<KitchenTicketResponse> markReady(@CurrentUser Integer staffId, @PathVariable Integer itemId) {
        return ResponseEntity.ok(kitchenService.markReady(staffId, itemId));
    }

    @PutMapping("/items/{itemId}/served")
    @Operation(summary = "Đã bưng món ra", description = "READY → SERVED. Bếp đặt xong hoặc nhân viên bưng ra đều có thể xác nhận")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<KitchenTicketResponse> markServed(@CurrentUser Integer staffId, @PathVariable Integer itemId) {
        return ResponseEntity.ok(kitchenService.markServed(staffId, itemId));
    }
}
