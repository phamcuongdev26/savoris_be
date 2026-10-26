package com.savoris.controller;

import com.savoris.dto.request.AreaRequest;
import com.savoris.dto.request.TableRequest;
import com.savoris.dto.response.AreaResponse;
import com.savoris.dto.response.TableResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.FloorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Table", description = "Sơ đồ bàn: khu vực và bàn của chi nhánh")
public class TableController {

    private final FloorService floorService;

    @GetMapping("/branches/{branchId}/tables")
    @PreAuthorize(Roles.ORDER_WRITE)
    @Operation(summary = "Sơ đồ bàn", description = "Danh sách bàn của chi nhánh và trạng thái (trống / có khách)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<TableResponse>> getTables(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(floorService.getTables(staffId, branchId));
    }

    @GetMapping("/branches/{branchId}/areas")
    @PreAuthorize(Roles.ORDER_WRITE)
    @Operation(summary = "Khu vực của chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<AreaResponse>> getAreas(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        return ResponseEntity.ok(floorService.getAreas(staffId, branchId));
    }

    @PostMapping("/branches/{branchId}/areas")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Thêm khu vực", description = "Ví dụ Tầng 1, Sân vườn")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AreaResponse> createArea(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                   @Valid @RequestBody AreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(floorService.createArea(staffId, branchId, request));
    }

    @PutMapping("/areas/{areaId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Đổi tên khu vực")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AreaResponse> renameArea(@CurrentUser Integer staffId, @PathVariable Integer areaId,
                                                   @Valid @RequestBody AreaRequest request) {
        return ResponseEntity.ok(floorService.renameArea(staffId, areaId, request));
    }

    @DeleteMapping("/areas/{areaId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Xoá khu vực", description = "Chỉ xoá được khu không còn bàn")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteArea(@CurrentUser Integer staffId, @PathVariable Integer areaId) {
        floorService.deleteArea(staffId, areaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/areas/{areaId}/tables")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Thêm bàn", description = "Tên bàn duy nhất trong khu, ví dụ A01")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TableResponse> createTable(@CurrentUser Integer staffId, @PathVariable Integer areaId,
                                                     @Valid @RequestBody TableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(floorService.createTable(staffId, areaId, request));
    }

    @PutMapping("/tables/{tableId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Sửa bàn")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TableResponse> updateTable(@CurrentUser Integer staffId, @PathVariable Integer tableId,
                                                     @Valid @RequestBody TableRequest request) {
        return ResponseEntity.ok(floorService.updateTable(staffId, tableId, request));
    }

    @DeleteMapping("/tables/{tableId}")
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Xoá bàn", description = "Bàn đã có đơn thì không xoá được (giữ lịch sử)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteTable(@CurrentUser Integer staffId, @PathVariable Integer tableId) {
        floorService.deleteTable(staffId, tableId);
        return ResponseEntity.noContent().build();
    }
}
