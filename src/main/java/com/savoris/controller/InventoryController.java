package com.savoris.controller;

import com.savoris.dto.request.StockImportRequest;
import com.savoris.dto.request.UpdateInventoryRequest;
import com.savoris.dto.response.StockImportResponse;
import com.savoris.dto.response.InventoryResponse;
import com.savoris.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/branch/{branchId}/ingredient/{ingredientId}")
    @Operation(summary = "Lấy kho nguyên liệu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.STOCK_READ)
    public ResponseEntity<InventoryResponse> getInventory(@CurrentUser Integer staffId, @PathVariable Integer branchId, @PathVariable Integer ingredientId) {
        return ResponseEntity.ok(inventoryService.getInventory(staffId, branchId, ingredientId));
    }

    @PutMapping("/branch/{branchId}/ingredient/{ingredientId}")
    @Operation(summary = "Cập nhật kho nguyên liệu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.STOCK)
    public ResponseEntity<InventoryResponse> updateInventory(@CurrentUser Integer staffId, @PathVariable Integer branchId, @PathVariable Integer ingredientId, @Valid @RequestBody UpdateInventoryRequest request) {
        return ResponseEntity.ok(inventoryService.updateInventory(staffId, branchId, ingredientId, request));
    }

    @PostMapping("/branch/{branchId}/import")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Nhập tồn kho (tay hoặc từ file Excel/CSV)",
        description = "rows: [{name, unit, quantity, minStock}]. Nguyên liệu chưa có sẽ được tạo (cần unit); số lượng là tồn thực tế hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<StockImportResponse> importStock(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                           @Valid @RequestBody StockImportRequest request) {
        return ResponseEntity.ok(inventoryService.importStock(staffId, branchId, request.rows()));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Danh sách kho theo chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.STOCK_READ)
    public ResponseEntity<List<InventoryResponse>> getByBranch(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(inventoryService.getInventoriesByBranch(staffId, branchId));
    }

    @GetMapping("/branch/{branchId}/low-stock")
    @Operation(summary = "Danh sách hàng hết/thiếu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.STOCK_READ)
    public ResponseEntity<List<InventoryResponse>> getLowStock(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(inventoryService.getLowStockItems(staffId, branchId));
    }
}
