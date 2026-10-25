package com.savoris.controller;

import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.BranchMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/branches/{branchId}/menu")
@RequiredArgsConstructor
@Tag(name = "Branch menu", description = "Menu của từng chi nhánh: báo hết món, giá riêng")
public class BranchMenuController {

    private final BranchMenuService branchMenuService;

    public record AvailabilityRequest(@NotNull(message = "available is required") Boolean available) {
    }

    // price null = back to the chain price
    public record PriceRequest(@DecimalMin(value = "0", message = "Price must not be negative") BigDecimal price) {
    }

    @GetMapping
    @Operation(summary = "Menu chi nhánh", description = "Giá đang áp dụng tại chi nhánh và món nào còn bán")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<BranchMenuItemResponse>> getMenu(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(branchMenuService.getMenu(staffId, branchId));
    }

    @PutMapping("/{menuItemId}/availability")
    @PreAuthorize(Roles.KITCHEN)
    @Operation(summary = "Báo hết món / bán lại", description = "{\"available\": false} = hết món; đơn mới gọi món này bị từ chối")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BranchMenuItemResponse> setAvailability(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                                  @PathVariable Integer menuItemId,
                                                                  @Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(branchMenuService.setAvailability(staffId, branchId, menuItemId, request.available()));
    }

    @PutMapping("/{menuItemId}/price")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Giá riêng của chi nhánh", description = "{\"price\": 60000}; {\"price\": null} = quay về giá chung của chuỗi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BranchMenuItemResponse> setPrice(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                           @PathVariable Integer menuItemId,
                                                           @Valid @RequestBody PriceRequest request) {
        return ResponseEntity.ok(branchMenuService.setPrice(staffId, branchId, menuItemId, request.price()));
    }
}
