package com.savoris.controller;

import com.savoris.dto.request.RecipeRequest;
import com.savoris.dto.response.RecipeLineResponse;
import com.savoris.entity.IngredientEntity;
import com.savoris.entity.RecipeEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.IngredientRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.repository.RecipeRepository;
import com.savoris.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/menu-items/{menuItemId}/recipe")
@RequiredArgsConstructor
@Tag(name = "Recipe", description = "Công thức món: nguyên liệu cho 1 phần, bếp nhận nấu thì kho tự trừ theo đây")
public class RecipeController {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final MenuItemRepository menuItemRepository;

    @GetMapping
    @PreAuthorize(Roles.STOCK_READ)
    @Operation(summary = "Xem công thức món")
    @SecurityRequirement(name = "bearerAuth")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RecipeLineResponse>> get(@PathVariable Integer menuItemId) {
        requireDish(menuItemId);
        return ResponseEntity.ok(lines(menuItemId));
    }

    @PutMapping
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Đặt công thức món", description = "Thay toàn bộ công thức (định lượng cho 1 phần). Danh sách rỗng = bỏ trừ kho cho món này")
    @SecurityRequirement(name = "bearerAuth")
    @Transactional
    public ResponseEntity<List<RecipeLineResponse>> replace(@PathVariable Integer menuItemId, @Valid @RequestBody RecipeRequest request) {
        requireDish(menuItemId);
        List<Integer> ids = request.lines().stream().map(RecipeRequest.Line::ingredientId).toList();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Each ingredient may appear only once in a recipe");
        }
        if (ingredientRepository.findAllById(ids).size() != ids.size()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Unknown ingredient in " + ids);
        }
        recipeRepository.deleteByMenuItemId(menuItemId);
        recipeRepository.flush();
        for (RecipeRequest.Line line : request.lines()) {
            RecipeEntity recipe = new RecipeEntity();
            recipe.setMenuItemId(menuItemId);
            recipe.setIngredientId(line.ingredientId());
            recipe.setQuantity(line.quantity());
            recipeRepository.save(recipe);
        }
        return ResponseEntity.ok(lines(menuItemId));
    }

    private void requireDish(Integer menuItemId) {
        if (!menuItemRepository.existsById(menuItemId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Dish not found: " + menuItemId);
        }
    }

    private List<RecipeLineResponse> lines(Integer menuItemId) {
        List<RecipeEntity> recipe = recipeRepository.findByMenuItemId(menuItemId);
        Map<Integer, IngredientEntity> ingredients = ingredientRepository
            .findAllById(recipe.stream().map(RecipeEntity::getIngredientId).toList()).stream()
            .collect(Collectors.toMap(IngredientEntity::getId, Function.identity()));
        return recipe.stream().map(r -> {
            IngredientEntity i = ingredients.get(r.getIngredientId());
            return new RecipeLineResponse(r.getIngredientId(), i.getName(), i.getUnit(), r.getQuantity());
        }).toList();
    }
}
