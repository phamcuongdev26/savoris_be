package com.savoris.controller;

import com.savoris.dto.request.CreateMenuCategoryRequest;
import com.savoris.dto.request.UpdateMenuCategoryRequest;
import com.savoris.dto.response.MenuCategoryResponse;
import com.savoris.service.MenuCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.savoris.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu-categories")
@RequiredArgsConstructor
@Tag(name = "Menu Category", description = "Menu category management endpoints")
public class MenuCategoryController {

    private final MenuCategoryService categoryService;

    @PostMapping
    @Operation(summary = "Tạo danh mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<MenuCategoryResponse> create(@Valid @RequestBody CreateMenuCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Lấy danh mục menu")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MenuCategoryResponse> get(@PathVariable Integer categoryId) {
        return ResponseEntity.ok(categoryService.getCategory(categoryId));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Cập nhật danh mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<MenuCategoryResponse> update(@PathVariable Integer categoryId, @Valid @RequestBody UpdateMenuCategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Xoá danh mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Integer categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm danh mục")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MenuCategoryResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(categoryService.searchCategories(q));
    }
}
