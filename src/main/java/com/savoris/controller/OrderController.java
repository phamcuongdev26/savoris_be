package com.savoris.controller;

import com.savoris.dto.request.AddOrderItemsRequest;
import com.savoris.dto.request.CancelOrderItemRequest;
import com.savoris.dto.request.CreateOrderRequest;
import com.savoris.dto.response.OrderResponse;
import com.savoris.security.CurrentUser;
import com.savoris.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.savoris.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "Order management endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Tạo đơn hàng", description = "Khi bếp quá tải, đơn mang đi/giao hàng bị từ chối với mã 503 BRANCH_BUSY; đơn tại bàn luôn được nhận")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ORDER_WRITE)
    public ResponseEntity<OrderResponse> createOrder(@CurrentUser Integer staffId,
                                                     @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(staffId, request));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Lấy đơn hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<OrderResponse> getOrder(@CurrentUser Integer staffId, @PathVariable Integer orderId) {
        return ResponseEntity.ok(orderService.getOrder(staffId, orderId));
    }

    @PutMapping("/{orderId}/complete")
    @Operation(summary = "Hoàn thành đơn hàng", description = "Chỉ hoàn thành được khi mọi món đã phục vụ hoặc đã huỷ")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.PAYMENT)
    public ResponseEntity<OrderResponse> completeOrder(@CurrentUser Integer staffId, @PathVariable Integer orderId) {
        return ResponseEntity.ok(orderService.completeOrder(staffId, orderId));
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Hủy đơn hàng", description = "Các món chưa phục vụ cũng bị gỡ khỏi hàng đợi bếp")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.PAYMENT)
    public ResponseEntity<OrderResponse> cancelOrder(@CurrentUser Integer staffId, @PathVariable Integer orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(staffId, orderId));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Danh sách đơn hàng đang mở theo chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<List<OrderResponse>> getOrdersByBranch(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(orderService.getOrdersByBranch(staffId, branchId));
    }

    @GetMapping("/branch/{branchId}/refunds-due")
    @Operation(summary = "Đơn chuyển khoản đã huỷ cần hoàn tiền", description = "Khách đã báo chuyển khoản hoặc tiền về sau khi đơn bị huỷ")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.PAYMENT)
    public ResponseEntity<List<OrderResponse>> getRefundsDue(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(orderService.getRefundsDue(staffId, branchId));
    }

    @PutMapping("/{orderId}/refund-settled")
    @Operation(summary = "Đã hoàn tiền cho đơn chuyển khoản bị huỷ")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.PAYMENT)
    public ResponseEntity<OrderResponse> settleRefund(@CurrentUser Integer staffId, @PathVariable Integer orderId) {
        return ResponseEntity.ok(orderService.settleRefund(staffId, orderId));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Danh sách đơn hàng theo khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ORDER_WRITE)
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(@CurrentUser Integer staffId, @PathVariable Integer customerId) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(staffId, customerId));
    }

    @PostMapping("/{orderId}/items")
    @PreAuthorize(Roles.ORDER_WRITE)
    @Operation(summary = "Gọi thêm món", description = "Thêm món vào đơn đang mở (chưa thanh toán); tổng tiền, khuyến mãi, VAT được tính lại")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> addItems(@CurrentUser Integer staffId, @PathVariable Integer orderId,
                                                  @Valid @RequestBody AddOrderItemsRequest request) {
        return ResponseEntity.ok(orderService.addItems(staffId, orderId, request));
    }

    @PutMapping("/{orderId}/items/{itemId}/cancel")
    @PreAuthorize(Roles.ORDER_WRITE)
    @Operation(summary = "Huỷ một món",
        description = "Món chưa nấu: nhân viên huỷ được. Món đang nấu / đã xong: chỉ quản lý. Món đã bưng ra: không huỷ được")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderResponse> cancelItem(@CurrentUser Integer staffId, @PathVariable Integer orderId,
                                                    @PathVariable Integer itemId,
                                                    @Valid @RequestBody(required = false) CancelOrderItemRequest request) {
        return ResponseEntity.ok(orderService.cancelItem(staffId, orderId, itemId, request != null ? request.getReason() : null));
    }
}
