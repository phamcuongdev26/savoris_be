package com.savoris.controller;

import com.savoris.dto.request.AssignShiftRequest;
import com.savoris.dto.request.CreateShiftRequest;
import com.savoris.dto.response.ShiftResponse;
import com.savoris.dto.response.StaffShiftResponse;
import com.savoris.dto.response.TimesheetResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.ShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Shift", description = "Ca làm việc, xếp lịch, chấm công, bảng công")
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping("/branches/{branchId}/shifts")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Tạo ca", description = "Ví dụ Ca sáng 07:00 - 15:00; giờ kết thúc nhỏ hơn giờ bắt đầu = ca qua đêm")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ShiftResponse> createShift(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                     @Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.createShift(staffId, branchId, request));
    }

    @GetMapping("/branches/{branchId}/shifts")
    @Operation(summary = "Danh sách ca của chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<List<ShiftResponse>> getShifts(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(shiftService.getShifts(staffId, branchId));
    }

    @PostMapping("/shifts/{shiftId}/assignments")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Xếp nhân viên vào ca", description = "Nhân viên phải thuộc chi nhánh của ca; không xếp ngày đã qua")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<StaffShiftResponse> assign(@CurrentUser Integer staffId, @PathVariable Integer shiftId,
                                                     @Valid @RequestBody AssignShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.assign(staffId, shiftId, request));
    }

    @DeleteMapping("/shift-assignments/{assignmentId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Bỏ xếp ca", description = "Không bỏ được khi nhân viên đã chấm công vào")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unassign(@CurrentUser Integer staffId, @PathVariable Integer assignmentId) {
        shiftService.unassign(staffId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branches/{branchId}/schedule")
    @Operation(summary = "Lịch làm việc của chi nhánh", description = "Tối đa 62 ngày, ví dụ ?from=2026-10-12&to=2026-10-18")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<List<StaffShiftResponse>> getSchedule(
        @CurrentUser Integer staffId, @PathVariable Integer branchId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shiftService.getSchedule(staffId, branchId, from, to));
    }

    @GetMapping("/shifts/me")
    @Operation(summary = "Lịch làm việc của tôi")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<List<StaffShiftResponse>> getMySchedule(
        @CurrentUser Integer staffId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shiftService.getMySchedule(staffId, from, to));
    }

    @GetMapping("/shifts/me/timesheet")
    @Operation(summary = "Bảng công của tôi", description = "Giờ làm và tiền công của bản thân trong khoảng ngày")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<TimesheetResponse> getMyTimesheet(
        @CurrentUser Integer staffId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shiftService.getMyTimesheet(staffId, from, to));
    }

    @PutMapping("/shift-assignments/{assignmentId}/check-in")
    @Operation(summary = "Chấm công vào", description = "Chỉ ca của chính mình, từ 30 phút trước giờ vào ca đến hết ca")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<StaffShiftResponse> checkIn(@CurrentUser Integer staffId, @PathVariable Integer assignmentId) {
        return ResponseEntity.ok(shiftService.checkIn(staffId, assignmentId));
    }

    @PutMapping("/shift-assignments/{assignmentId}/check-out")
    @Operation(summary = "Chấm công ra")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.RESTAURANT)
    public ResponseEntity<StaffShiftResponse> checkOut(@CurrentUser Integer staffId, @PathVariable Integer assignmentId) {
        return ResponseEntity.ok(shiftService.checkOut(staffId, assignmentId));
    }

    @GetMapping("/branches/{branchId}/timesheet")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Bảng công", description = "Giờ làm thực tế (vào → ra) và tiền công = giờ x lương giờ, theo từng nhân viên")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<TimesheetResponse>> getTimesheet(
        @CurrentUser Integer staffId, @PathVariable Integer branchId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(shiftService.getTimesheet(staffId, branchId, from, to));
    }
}
