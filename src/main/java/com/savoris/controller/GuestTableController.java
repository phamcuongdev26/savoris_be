package com.savoris.controller;

import com.savoris.dto.request.AddOrderItemsRequest;
import com.savoris.dto.response.GuestOrderResponse;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.service.GuestTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Under /public: guests do not log in, the QR token of the table is their key
@RestController
@RequestMapping("/public/tables/{qrToken}")
@RequiredArgsConstructor
@Tag(name = "Guest QR ordering", description = "Khách quét QR trên bàn để xem menu và gọi món, không cần đăng nhập")
public class GuestTableController {

    private final GuestTableService guestTableService;

    @GetMapping
    @Operation(summary = "Thông tin bàn và menu", description = "Chi nhánh, khu vực, tên bàn và menu đang bán tại chi nhánh")
    public ResponseEntity<GuestTableResponse> getTable(@PathVariable String qrToken) {
        return ResponseEntity.ok(guestTableService.getTable(qrToken));
    }

    @GetMapping("/order")
    @Operation(summary = "Hoá đơn đang mở của bàn", description = "204 khi bàn chưa gọi món")
    public ResponseEntity<GuestOrderResponse> getOrder(@PathVariable String qrToken) {
        return guestTableService.getOpenOrder(qrToken)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/orders")
    @Operation(summary = "Khách gọi món", description = "Món được thêm vào hoá đơn đang mở của bàn (hoặc mở hoá đơn mới) và chuyển thẳng xuống bếp")
    public ResponseEntity<GuestOrderResponse> placeOrder(@PathVariable String qrToken,
                                                         @Valid @RequestBody AddOrderItemsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestTableService.placeOrder(qrToken, request));
    }
}
