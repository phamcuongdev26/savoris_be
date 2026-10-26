package com.savoris.controller;

import com.savoris.dto.request.CreatePaymentRequest;
import com.savoris.dto.response.PaymentResponse;
import com.savoris.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment management endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Thanh toán tiền mặt", description = "Nhập tiền khách đưa (cashReceived), server tính tiền thối. Chỉ nhận CASH")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.PAYMENT)
    public ResponseEntity<PaymentResponse> processPayment(@CurrentUser Integer staffId, @Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(staffId, request));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize(Roles.PAYMENT)
    @Operation(summary = "Lấy thông tin thanh toán")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> getPayment(@CurrentUser Integer staffId, @PathVariable Integer paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(staffId, paymentId));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize(Roles.PAYMENT)
    @Operation(summary = "Lấy thanh toán theo đơn hàng")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(@CurrentUser Integer staffId, @PathVariable Integer orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrder(staffId, orderId));
    }

    @PutMapping("/{paymentId}/refund")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Hoàn tiền", description = "Chỉ quản lý / chủ chuỗi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PaymentResponse> refundPayment(@CurrentUser Integer staffId, @PathVariable Integer paymentId) {
        return ResponseEntity.ok(paymentService.refundPayment(staffId, paymentId));
    }
}
