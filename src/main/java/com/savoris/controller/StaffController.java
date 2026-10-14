package com.savoris.controller;

import com.savoris.dto.request.CreateStaffRequest;
import com.savoris.dto.request.ResetPasswordRequest;
import com.savoris.dto.request.UpdateStaffRequest;
import com.savoris.dto.response.StaffDetailResponse;
import com.savoris.dto.response.ErrorResponse;
import com.savoris.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff management endpoints")
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @Operation(summary = "Tạo nhân viên mới")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Nhân viên đã được tạo"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<StaffDetailResponse> createStaff(@CurrentUser Integer actorId, @Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createStaff(actorId, request));
    }

    @GetMapping("/{staffId}")
    @Operation(summary = "Lấy thông tin nhân viên")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thông tin nhân viên"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StaffDetailResponse> getStaff(@CurrentUser Integer actorId, @PathVariable Integer staffId) {
        return ResponseEntity.ok(staffService.getStaff(actorId, staffId));
    }

    @PutMapping("/{staffId}")
    @Operation(summary = "Cập nhật nhân viên")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<StaffDetailResponse> updateStaff(@CurrentUser Integer actorId, @PathVariable Integer staffId, @Valid @RequestBody UpdateStaffRequest request) {
        return ResponseEntity.ok(staffService.updateStaff(actorId, staffId, request));
    }

    @DeleteMapping("/{staffId}")
    @Operation(summary = "Xoá nhân viên")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<Void> deleteStaff(@CurrentUser Integer actorId, @PathVariable Integer staffId) {
        staffService.deleteStaff(actorId, staffId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Danh sách nhân viên theo chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<List<StaffDetailResponse>> getStaffByBranch(@CurrentUser Integer actorId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(staffService.getStaffByBranch(actorId, branchId));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm nhân viên")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<List<StaffDetailResponse>> searchStaff(@RequestParam(name = "q") String query) {
        return ResponseEntity.ok(staffService.searchStaff(query));
    }

    @PutMapping("/{staffId}/password")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Đặt lại mật khẩu cho nhân viên",
        description = "Dùng khi nhân viên quên mật khẩu. Quản lý chỉ đặt lại cho nhân viên chi nhánh mình; mọi phiên đăng nhập cũ bị thu hồi")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> resetPassword(@CurrentUser Integer actorId, @PathVariable Integer staffId,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        staffService.resetPassword(actorId, staffId, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
