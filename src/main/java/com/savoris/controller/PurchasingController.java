package com.savoris.controller;

import com.savoris.dto.request.CreateIngredientRequest;
import com.savoris.dto.request.CreatePurchaseOrderRequest;
import com.savoris.dto.request.CreateSupplierRequest;
import com.savoris.dto.request.ReceivePurchaseOrderRequest;
import com.savoris.dto.response.IngredientResponse;
import com.savoris.dto.response.PurchaseOrderResponse;
import com.savoris.dto.response.SupplierResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.PurchasingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Purchasing", description = "Nhà cung cấp, nguyên liệu, phiếu nhập hàng")
public class PurchasingController {

    private final PurchasingService purchasingService;

    // ---------------------------------------------------------------- suppliers

    @PostMapping("/suppliers")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Thêm nhà cung cấp")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchasingService.createSupplier(request));
    }

    @PutMapping("/suppliers/{supplierId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Sửa nhà cung cấp")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupplierResponse> updateSupplier(@PathVariable Integer supplierId, @Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(purchasingService.updateSupplier(supplierId, request));
    }

    @PutMapping("/suppliers/{supplierId}/deactivate")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Ngừng hợp tác nhà cung cấp")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<SupplierResponse> deactivateSupplier(@PathVariable Integer supplierId) {
        return ResponseEntity.ok(purchasingService.deactivateSupplier(supplierId));
    }

    @GetMapping("/suppliers")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Danh sách nhà cung cấp đang hợp tác")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SupplierResponse>> getSuppliers() {
        return ResponseEntity.ok(purchasingService.getSuppliers());
    }

    // ---------------------------------------------------------------- ingredients

    @PostMapping("/ingredients")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Thêm nguyên liệu", description = "Danh mục nguyên liệu dùng chung cả chuỗi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<IngredientResponse> createIngredient(@Valid @RequestBody CreateIngredientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchasingService.createIngredient(request));
    }

    @GetMapping("/ingredients")
    @PreAuthorize(Roles.STOCK_READ)
    @Operation(summary = "Danh sách nguyên liệu")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<IngredientResponse>> getIngredients() {
        return ResponseEntity.ok(purchasingService.getIngredients());
    }

    // ---------------------------------------------------------------- purchase orders

    @PostMapping("/purchase-orders")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Lập phiếu nhập (nháp)", description = "Kho hoặc quản lý lập; chờ quản lý duyệt đặt hàng")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PurchaseOrderResponse> create(@CurrentUser Integer staffId, @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchasingService.createPurchaseOrder(staffId, request));
    }

    @GetMapping("/purchase-orders/{purchaseOrderId}")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Xem phiếu nhập")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PurchaseOrderResponse> get(@CurrentUser Integer staffId, @PathVariable Integer purchaseOrderId) {
        return ResponseEntity.ok(purchasingService.getPurchaseOrder(staffId, purchaseOrderId));
    }

    @GetMapping("/purchase-orders/branch/{branchId}")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Phiếu nhập của chi nhánh", description = "Mới nhất trước")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PurchaseOrderResponse>> getByBranch(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(purchasingService.getPurchaseOrders(staffId, branchId));
    }

    @PutMapping("/purchase-orders/{purchaseOrderId}/order")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Duyệt & đặt hàng", description = "DRAFT → ORDERED; chỉ quản lý / chủ chuỗi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PurchaseOrderResponse> placeOrder(@CurrentUser Integer staffId, @PathVariable Integer purchaseOrderId) {
        return ResponseEntity.ok(purchasingService.placeOrder(staffId, purchaseOrderId));
    }

    @PutMapping("/purchase-orders/{purchaseOrderId}/receive")
    @PreAuthorize(Roles.STOCK)
    @Operation(summary = "Nhận hàng", description = "ORDERED → RECEIVED, cộng kho. Không gửi body = nhận đủ; gửi items để ghi số lượng thực nhận")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PurchaseOrderResponse> receive(@CurrentUser Integer staffId, @PathVariable Integer purchaseOrderId,
                                                         @Valid @RequestBody(required = false) ReceivePurchaseOrderRequest request) {
        return ResponseEntity.ok(purchasingService.receive(staffId, purchaseOrderId, request));
    }

    @PutMapping("/purchase-orders/{purchaseOrderId}/cancel")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Huỷ phiếu nhập", description = "Chỉ khi chưa nhận hàng")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PurchaseOrderResponse> cancel(@CurrentUser Integer staffId, @PathVariable Integer purchaseOrderId) {
        return ResponseEntity.ok(purchasingService.cancel(staffId, purchaseOrderId));
    }
}
