package com.savoris.service.impl;

import com.savoris.constant.StockMovementType;
import com.savoris.dto.request.StockImportRequest;
import com.savoris.dto.request.UpdateInventoryRequest;
import com.savoris.dto.response.StockImportResponse;
import com.savoris.entity.IngredientEntity;
import com.savoris.dto.response.InventoryResponse;
import com.savoris.entity.BranchInventoryEntity;
import com.savoris.entity.OrderItemEntity;
import com.savoris.entity.RecipeEntity;
import com.savoris.entity.StockMovementEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchInventoryRepository;
import com.savoris.repository.IngredientRepository;
import com.savoris.repository.RecipeRepository;
import com.savoris.repository.StockMovementRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final BranchInventoryRepository inventoryRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final IngredientRepository ingredientRepository;

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Integer staffId, Integer branchId, Integer ingredientId) {
        branchAccessGuard.check(staffId, branchId);
        return InventoryResponse.from(findInventory(branchId, ingredientId));
    }

    @Override
    @Transactional
    public InventoryResponse updateInventory(Integer staffId, Integer branchId, Integer ingredientId, UpdateInventoryRequest request) {
        branchAccessGuard.check(staffId, branchId);
        BranchInventoryEntity inventory = findInventory(branchId, ingredientId);

        // Stock count correction: keep the difference in the stock history
        BigDecimal newQuantity = BigDecimal.valueOf(request.getQuantity());
        StockMovementEntity movement = new StockMovementEntity();
        movement.setBranchId(branchId);
        movement.setIngredientId(ingredientId);
        movement.setType(StockMovementType.ADJUSTMENT);
        movement.setQuantity(newQuantity.subtract(inventory.getQuantity()));
        movement.setNote(request.getNotes());
        movement.setCreatedBy(staffId);
        stockMovementRepository.save(movement);

        inventory.setQuantity(newQuantity);
        BranchInventoryEntity updated = inventoryRepository.save(inventory);
        log.info("Updated inventory for branch: {}, ingredient: {}", branchId, ingredientId);
        return InventoryResponse.from(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoriesByBranch(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return inventoryRepository.findByBranchId(branchId)
            .stream()
            .map(InventoryResponse::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockItems(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return inventoryRepository.findLowStock(branchId)
            .stream()
            .map(InventoryResponse::from)
            .toList();
    }

    // Called when a cook starts a dish: ingredients leave the store according to the recipe.
    // Stock may go below zero (the dish is already being cooked); low-stock reports surface that.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductForDish(Integer branchId, OrderItemEntity item, Integer staffId) {
        LocalDateTime now = LocalDateTime.now();
        for (RecipeEntity recipe : recipeRepository.findByMenuItemId(item.getMenuItemId())) {
            BigDecimal amount = recipe.getQuantity().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (inventoryRepository.deduct(branchId, recipe.getIngredientId(), amount, now) == 0) {
                log.warn("Branch {} does not stock ingredient {} used by {}", branchId, recipe.getIngredientId(), item.getItemName());
                continue;
            }
            StockMovementEntity movement = new StockMovementEntity();
            movement.setBranchId(branchId);
            movement.setIngredientId(recipe.getIngredientId());
            movement.setType(StockMovementType.EXPORT_KITCHEN);
            movement.setQuantity(amount.negate());
            movement.setOrderId(item.getOrderId());
            movement.setNote(item.getQuantity() + " x " + item.getItemName());
            movement.setCreatedBy(staffId);
            stockMovementRepository.save(movement);
        }
    }

    @Override
    public void restockForDish(Integer branchId, OrderItemEntity item, Integer staffId) {
        LocalDateTime now = LocalDateTime.now();
        for (RecipeEntity recipe : recipeRepository.findByMenuItemId(item.getMenuItemId())) {
            BigDecimal amount = recipe.getQuantity().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (inventoryRepository.add(branchId, recipe.getIngredientId(), amount, now) == 0) {
                continue;
            }
            StockMovementEntity movement = new StockMovementEntity();
            movement.setBranchId(branchId);
            movement.setIngredientId(recipe.getIngredientId());
            movement.setType(StockMovementType.ADJUSTMENT);
            movement.setQuantity(amount);
            movement.setOrderId(item.getOrderId());
            movement.setNote("Trả lại kho: huỷ " + item.getQuantity() + " x " + item.getItemName());
            movement.setCreatedBy(staffId);
            stockMovementRepository.save(movement);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean enoughFor(Integer branchId, Integer menuItemId, int portions) {
        List<RecipeEntity> recipe = recipeRepository.findByMenuItemId(menuItemId);
        if (recipe.isEmpty()) {
            return true;
        }
        Map<Integer, BigDecimal> stock = stockOf(branchId);
        BigDecimal times = BigDecimal.valueOf(portions);
        return recipe.stream().allMatch(r -> stock.getOrDefault(r.getIngredientId(), BigDecimal.ZERO)
            .compareTo(r.getQuantity().multiply(times)) >= 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> outOfStock(Integer branchId, Collection<Integer> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return Set.of();
        }
        Map<Integer, BigDecimal> stock = stockOf(branchId);
        return recipeRepository.findByMenuItemIdIn(menuItemIds).stream()
            .filter(r -> stock.getOrDefault(r.getIngredientId(), BigDecimal.ZERO).compareTo(r.getQuantity()) < 0)
            .map(RecipeEntity::getMenuItemId)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public StockImportResponse importStock(Integer staffId, Integer branchId, List<StockImportRequest.Row> rows) {
        branchAccessGuard.check(staffId, branchId);
        // Ingredients are matched by name, ignoring case and spacing, so a re-import updates instead of duplicating
        Map<String, IngredientEntity> byName = new HashMap<>();
        for (IngredientEntity i : ingredientRepository.findAll()) {
            byName.put(key(i.getName()), i);
        }
        int created = 0;
        int updated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (StockImportRequest.Row row : rows) {
            IngredientEntity ingredient = byName.get(key(row.name()));
            if (ingredient == null) {
                if (row.unit() == null || row.unit().isBlank()) {
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Unit is required for the new ingredient " + row.name().trim());
                }
                ingredient = new IngredientEntity();
                ingredient.setCode(nextCode());
                ingredient.setName(row.name().trim());
                ingredient.setUnit(row.unit().trim());
                ingredient.setMinStock(row.minStock() != null ? row.minStock() : BigDecimal.ZERO);
                ingredient = ingredientRepository.save(ingredient);
                byName.put(key(ingredient.getName()), ingredient);
                created++;
            } else if (row.minStock() != null) {
                ingredient.setMinStock(row.minStock());
                ingredientRepository.save(ingredient);
            }

            inventoryRepository.createIfAbsent(branchId, ingredient.getId(), now);
            BranchInventoryEntity inventory = findInventory(branchId, ingredient.getId());
            BigDecimal difference = row.quantity().subtract(inventory.getQuantity());
            if (difference.signum() != 0) {
                StockMovementEntity movement = new StockMovementEntity();
                movement.setBranchId(branchId);
                movement.setIngredientId(ingredient.getId());
                movement.setType(StockMovementType.ADJUSTMENT);
                movement.setQuantity(difference);
                movement.setNote("Kiểm kho / nhập từ file");
                movement.setCreatedBy(staffId);
                stockMovementRepository.save(movement);
            }
            inventory.setQuantity(row.quantity());
            inventoryRepository.save(inventory);
            updated++;
        }
        log.info("Branch {}: stock import by staff {}, {} rows, {} new ingredients", branchId, staffId, rows.size(), created);
        return new StockImportResponse(created, updated);
    }

    private Map<Integer, BigDecimal> stockOf(Integer branchId) {
        return inventoryRepository.findByBranchId(branchId).stream()
            .collect(Collectors.toMap(BranchInventoryEntity::getIngredientId, BranchInventoryEntity::getQuantity));
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // NL0001, NL0002…: the first free code
    private String nextCode() {
        long n = ingredientRepository.count() + 1;
        while (ingredientRepository.findByCode(String.format("NL%04d", n)).isPresent()) {
            n++;
        }
        return String.format("NL%04d", n);
    }

    private BranchInventoryEntity findInventory(Integer branchId, Integer ingredientId) {
        return inventoryRepository.findByBranchIdAndIngredientId(branchId, ingredientId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                "Ingredient " + ingredientId + " is not stocked at branch " + branchId));
    }
}
