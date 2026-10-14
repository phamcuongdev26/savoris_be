package com.savoris.controller;

import com.savoris.dto.request.CreateReservationRequest;
import com.savoris.dto.response.ReservationResponse;
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

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Danh sách đặt bàn theo chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReservationResponse>> getByBranch(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(reservationService.getReservationsByBranch(staffId, branchId));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Danh sách đặt bàn theo khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.FRONT_OF_HOUSE)
    public ResponseEntity<List<ReservationResponse>> getByCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(reservationService.getReservationsByCustomer(customerId));
    }
}
