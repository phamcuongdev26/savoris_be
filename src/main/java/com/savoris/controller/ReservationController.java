package com.savoris.controller;

import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.request.GuestReservationRequest;
import com.savoris.dto.response.ReservationResponse;
import com.savoris.dto.response.SlotAvailabilityResponse;
import org.springframework.format.annotation.DateTimeFormat;
import com.savoris.service.ReservationService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservation", description = "Reservation management endpoints")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "Tạo đặt bàn")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<ReservationResponse> createReservation(@CurrentUser Integer staffId, @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(staffId, request));
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Lấy đặt bàn")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReservationResponse> getReservation(@CurrentUser Integer staffId, @PathVariable Integer reservationId) {
        return ResponseEntity.ok(reservationService.getReservation(staffId, reservationId));
    }

    @PutMapping("/{reservationId}/confirm")
    @Operation(summary = "Xác nhận đặt bàn")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<ReservationResponse> confirmReservation(@CurrentUser Integer staffId, @PathVariable Integer reservationId) {
        return ResponseEntity.ok(reservationService.confirmReservation(staffId, reservationId));
    }

    @PutMapping("/{reservationId}/cancel")
    @Operation(summary = "Hủy đặt bàn")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<ReservationResponse> cancelReservation(@CurrentUser Integer staffId, @PathVariable Integer reservationId) {
        return ResponseEntity.ok(reservationService.cancelReservation(staffId, reservationId));
    }

    @PutMapping("/{reservationId}/no-show")
    @Operation(summary = "Đánh dấu khách không đến")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<ReservationResponse> markNoShow(@CurrentUser Integer staffId, @PathVariable Integer reservationId) {
        return ResponseEntity.ok(reservationService.markNoShow(staffId, reservationId));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Danh sách đặt bàn theo chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReservationResponse>> getByBranch(@CurrentUser Integer staffId, @PathVariable Integer branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) com.savoris.constant.ReservationStatus status) {
        LocalDate selectedDate = date == null ? LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")) : date;
        LocalDateTime start = selectedDate.atStartOfDay();
        LocalDateTime end = selectedDate.plusDays(1).atStartOfDay();
        return ResponseEntity.ok(reservationService.getReservationsByBranch(staffId, branchId).stream()
            .filter(r -> r.getReservationDate() != null && !r.getReservationDate().isBefore(start) && r.getReservationDate().isBefore(end))
            .filter(r -> status == null || r.getStatus() == status)
            .toList());
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Danh sách đặt bàn theo khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<List<ReservationResponse>> getByCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(reservationService.getReservationsByCustomer(customerId));
    }

    // ── Public guest booking endpoints (no JWT required) ─────────────────────

    @GetMapping("/guest/slots")
    @Operation(summary = "Khung giờ còn trống (public)", description = "30-phút/slot. Trả danh sách slot + số khách đã đặt và gợi ý slot thay thế nếu đầy.")
    public ResponseEntity<List<SlotAvailabilityResponse>> getSlots(
            @RequestParam Integer branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationService.getAvailableSlots(branchId, date));
    }

    @PostMapping("/guest")
    @Operation(summary = "Đặt bàn từ trang public (không cần đăng nhập)")
    public ResponseEntity<ReservationResponse> createGuestReservation(
            @Valid @RequestBody GuestReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createGuestReservation(request));
    }
}
