package com.savoris.controller;

import com.savoris.dto.request.CreateMenuItemRequest;
import com.savoris.dto.request.UpdateMenuItemRequest;
import com.savoris.dto.response.MenuItemResponse;
import com.savoris.service.MenuItemService;
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
@RequestMapping("/menu-items")
@RequiredArgsConstructor
@Tag(name = "Menu Item", description = "Menu item management endpoints")
public class MenuItemController {

    private final MenuItemService itemService;

    @PostMapping
    @Operation(summary = "Tạo mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.createItem(request));
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "Lấy mục menu")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MenuItemResponse> get(@PathVariable Integer itemId) {
        return ResponseEntity.ok(itemService.getItem(itemId));
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Cập nhật mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<MenuItemResponse> update(@PathVariable Integer itemId, @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(itemService.updateItem(itemId, request));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Xoá mục menu")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Integer itemId) {
        itemService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Danh sách mục theo danh mục")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MenuItemResponse>> getByCategory(@PathVariable Integer categoryId) {
        return ResponseEntity.ok(itemService.getItemsByCategory(categoryId));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm mục menu")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MenuItemResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(itemService.searchItems(q));
    }
}
