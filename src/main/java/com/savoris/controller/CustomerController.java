package com.savoris.controller;

import com.savoris.dto.request.CreateCustomerRequest;
import com.savoris.dto.response.CustomerResponse;
import com.savoris.dto.response.PointTransactionResponse;
import com.savoris.service.CustomerService;
import com.savoris.service.LoyaltyService;
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
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer management endpoints")
public class CustomerController {

    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;

    @PostMapping
    @Operation(summary = "Tạo khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Lấy thông tin khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(customerService.getCustomer(customerId));
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Cập nhật khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Integer customerId, @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(customerId, request));
    }

    @DeleteMapping("/{customerId}")
    @Operation(summary = "Xoá khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<Void> deleteCustomer(@PathVariable Integer customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<List<CustomerResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(customerService.searchCustomers(q));
    }

    @GetMapping("/by-phone")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    @Operation(summary = "Tìm khách theo số điện thoại", description = "Phục vụ hỏi SĐT để tích / dùng điểm")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CustomerResponse> findByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(customerService.findByPhone(phone));
    }

    @GetMapping("/{customerId}/points")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    @Operation(summary = "Lịch sử điểm", description = "Điểm cộng khi hoàn tất đơn, trừ khi dùng; mới nhất trước")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PointTransactionResponse>> pointHistory(@PathVariable Integer customerId) {
        return ResponseEntity.ok(loyaltyService.history(customerId));
    }
}
