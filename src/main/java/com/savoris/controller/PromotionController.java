package com.savoris.controller;

import com.savoris.dto.request.CreatePromotionRequest;
import com.savoris.dto.response.PromotionCheckResponse;
import com.savoris.dto.response.PromotionResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotion", description = "Mã khuyến mãi")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tạo mã khuyến mãi", description = "PERCENT (có thể giới hạn maxDiscount) hoặc FIXED_AMOUNT; branchIds rỗng = cả chuỗi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.createPromotion(request));
    }

    @PutMapping("/{promotionId}")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Sửa mã khuyến mãi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromotionResponse> update(@PathVariable Integer promotionId, @Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.ok(promotionService.updatePromotion(promotionId, request));
    }

    @PutMapping("/{promotionId}/deactivate")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Ngừng mã khuyến mãi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromotionResponse> deactivate(@PathVariable Integer promotionId) {
        return ResponseEntity.ok(promotionService.deactivatePromotion(promotionId));
    }

    @GetMapping
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    @Operation(summary = "Danh sách mã khuyến mãi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<PromotionResponse>> list() {
        return ResponseEntity.ok(promotionService.getPromotions());
    }

    @GetMapping("/check")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    @Operation(summary = "Thử mã", description = "Xem mã có dùng được cho đơn này không và được giảm bao nhiêu (không trừ lượt dùng)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromotionCheckResponse> check(@CurrentUser Integer staffId, @RequestParam String code,
                                                        @RequestParam Integer branchId, @RequestParam BigDecimal orderAmount) {
        return ResponseEntity.ok(promotionService.check(staffId, code, branchId, orderAmount));
    }
}
