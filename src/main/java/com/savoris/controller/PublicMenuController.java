package com.savoris.controller;

import com.savoris.dto.response.PublicMenuResponse;
import com.savoris.service.GuestTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Guest QR ordering", description = "Khách quét QR trên bàn để xem menu và gọi món, không cần đăng nhập")
public class PublicMenuController {

    private final GuestTableService guestTableService;

    @GetMapping("/public/menu")
    @Operation(summary = "Menu chung của chuỗi", description = "Trang chủ cho khách: các món đang bán với giá chung; quét QR tại bàn để gọi món")
    public ResponseEntity<PublicMenuResponse> getMenu() {
        return ResponseEntity.ok(guestTableService.getChainMenu());
    }
}
