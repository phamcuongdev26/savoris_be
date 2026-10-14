package com.savoris.controller;

import com.savoris.dto.request.CreateBranchRequest;
import com.savoris.dto.request.UpdateBranchRequest;
import com.savoris.dto.response.BranchResponse;
import com.savoris.dto.response.ErrorResponse;
import com.savoris.service.BranchService;
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
@RequestMapping("/branches")
@RequiredArgsConstructor
@Tag(name = "Branch", description = "Branch management endpoints")
public class BranchController {

    private final BranchService branchService;

    @PostMapping
    @Operation(
        summary = "Tạo chi nhánh mới",
        description = "Tạo một chi nhánh mới với thông tin cơ bản"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Chi nhánh đã được tạo"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<BranchResponse> createBranch(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse response = branchService.createBranch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{branchId}")
    @Operation(
        summary = "Lấy thông tin chi nhánh",
        description = "Lấy chi tiết thông tin của một chi nhánh"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thông tin chi nhánh"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BranchResponse> getBranch(@PathVariable Integer branchId) {
        BranchResponse response = branchService.getBranch(branchId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{branchId}")
    @Operation(
        summary = "Cập nhật chi nhánh",
        description = "Cập nhật thông tin của chi nhánh"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Chi nhánh đã được cập nhật"),
        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize(Roles.MANAGEMENT)
    public ResponseEntity<BranchResponse> updateBranch(
        @CurrentUser Integer staffId,
        @PathVariable Integer branchId,
        @Valid @RequestBody UpdateBranchRequest request
    ) {
        BranchResponse response = branchService.updateBranch(staffId, branchId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{branchId}")
    @Operation(
        summary = "Xoá chi nhánh",
        description = "Deactivate chi nhánh (đánh dấu là không hoạt động)"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Chi nhánh đã được xoá"),
        @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> deleteBranch(@PathVariable Integer branchId) {
        branchService.deleteBranch(branchId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(
        summary = "Danh sách chi nhánh",
        description = "Lấy danh sách tất cả chi nhánh hoạt động"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Danh sách chi nhánh"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<BranchResponse>> getAllBranches() {
        List<BranchResponse> response = branchService.getAllBranches();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(
        summary = "Tìm kiếm chi nhánh",
        description = "Tìm kiếm chi nhánh theo tên"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Danh sách chi nhánh tìm kiếm được"),
        @ApiResponse(responseCode = "401", description = "Chưa xác thực",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<BranchResponse>> searchBranches(
        @RequestParam(name = "q") String query
    ) {
        List<BranchResponse> response = branchService.searchBranches(query);
        return ResponseEntity.ok(response);
    }
}
