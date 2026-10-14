package com.savoris.controller;

import com.savoris.dto.response.DishBranchResponse;
import com.savoris.dto.response.DishExclusionResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.BranchMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Branch menu", description = "Menu của từng chi nhánh: báo hết món, giá riêng")
public class DishBranchController {

    private final BranchMenuService branchMenuService;

    public record BranchIdsRequest(@NotNull(message = "branchIds is required") List<Integer> branchIds) {
    }

    @GetMapping("/menu-items/{itemId}/branches")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Chi nhánh bán món này", description = "Mọi chi nhánh đang hoạt động, offered = có bán món này hay không")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<DishBranchResponse>> getBranches(@PathVariable Integer itemId) {
        return ResponseEntity.ok(branchMenuService.getDishBranches(itemId));
    }

    @PutMapping("/menu-items/{itemId}/branches")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Chọn chi nhánh bán món", description = "{\"branchIds\": [1, 3]} = chỉ CN có id 1 và 3 bán món này; chi nhánh khác không hiện món")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<DishBranchResponse>> setBranches(@CurrentUser Integer staffId, @PathVariable Integer itemId,
                                                                @Valid @RequestBody BranchIdsRequest request) {
        return ResponseEntity.ok(branchMenuService.setDishBranches(staffId, itemId, request.branchIds()));
    }

    @GetMapping("/menu-items/branch-exclusions")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Món không bán ở chi nhánh nào", description = "Danh sách cặp (món, chi nhánh) không bán; dùng để hiện món theo từng chi nhánh")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<DishExclusionResponse>> getExclusions() {
        return ResponseEntity.ok(branchMenuService.getExclusions());
    }
}
