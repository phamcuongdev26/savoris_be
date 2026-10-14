package com.savoris.service.impl;

import com.savoris.constant.BranchStatus;
import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.dto.response.DishBranchResponse;
import com.savoris.dto.response.DishExclusionResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.BranchMenuItemEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchMenuItemRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.BranchMenuService;
import com.savoris.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The chain menu as one branch sells it: a dish can be sold out at a branch (e.g. ran out at peak hours)
 * or carry a branch price; anything not overridden follows the chain menu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchMenuServiceImpl implements BranchMenuService {

    private final MenuItemRepository menuItemRepository;
    private final BranchMenuItemRepository branchMenuItemRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final InventoryService inventoryService;

    @Override
    @Transactional(readOnly = true)
    public List<BranchMenuItemResponse> getMenu(Integer staffId, Integer branchId) {
        checkBranch(staffId, branchId);
        return getPublicMenu(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchMenuItemResponse> getPublicMenu(Integer branchId) {
        Map<Integer, BranchMenuItemEntity> overrides = branchMenuItemRepository.findByBranchId(branchId).stream()
            .collect(Collectors.toMap(BranchMenuItemEntity::getMenuItemId, Function.identity()));
        // A dish the owner did not give to this branch is not on its menu at all
        List<MenuItemEntity> dishes = menuItemRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByCategoryIdAscNameAsc().stream()
            .filter(item -> overrides.get(item.getId()) == null || Boolean.TRUE.equals(overrides.get(item.getId()).getOffered()))
            .toList();
        // Ready-made dishes run out with their stock, not by the chef's say-so
        Set<Integer> outOfStock = inventoryService.outOfStock(branchId, dishes.stream()
            .filter(item -> Boolean.FALSE.equals(item.getNeedsKitchen())).map(MenuItemEntity::getId).toList());
        return dishes.stream()
            .map(item -> withStock(toResponse(item, overrides.get(item.getId())), outOfStock.contains(item.getId())))
            .toList();
    }

    @Override
    @Transactional
    public BranchMenuItemResponse setAvailability(Integer staffId, Integer branchId, Integer menuItemId, boolean available) {
        checkBranch(staffId, branchId);
        MenuItemEntity item = findDish(menuItemId);
        BranchMenuItemEntity entry = entryFor(branchId, menuItemId);
        entry.setIsAvailable(available);
        branchMenuItemRepository.save(entry);
        log.info("Branch {}: {} is now {}", branchId, item.getName(), available ? "available" : "sold out");
        return toResponse(item, entry);
    }

    @Override
    @Transactional
    public BranchMenuItemResponse setPrice(Integer staffId, Integer branchId, Integer menuItemId, BigDecimal price) {
        checkBranch(staffId, branchId);
        MenuItemEntity item = findDish(menuItemId);
        BranchMenuItemEntity entry = entryFor(branchId, menuItemId);
        entry.setPrice(price);
        branchMenuItemRepository.save(entry);
        log.info("Branch {}: {} price set to {}", branchId, item.getName(), price != null ? price : "chain price");
        return toResponse(item, entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishBranchResponse> getDishBranches(Integer menuItemId) {
        findDish(menuItemId);
        Map<Integer, BranchMenuItemEntity> entries = branchMenuItemRepository.findByMenuItemId(menuItemId).stream()
            .collect(Collectors.toMap(BranchMenuItemEntity::getBranchId, Function.identity()));
        return branchRepository.findByStatus(BranchStatus.ACTIVE).stream()
            .sorted(Comparator.comparing(BranchEntity::getCode))
            .map(b -> new DishBranchResponse(b.getId(), b.getCode(), b.getName(),
                entries.get(b.getId()) == null || Boolean.TRUE.equals(entries.get(b.getId()).getOffered())))
            .toList();
    }

    @Override
    @Transactional
    public List<DishBranchResponse> setDishBranches(Integer staffId, Integer menuItemId, List<Integer> branchIds) {
        MenuItemEntity item = findDish(menuItemId);
        Set<Integer> selling = Set.copyOf(branchIds);
        for (BranchEntity branch : branchRepository.findByStatus(BranchStatus.ACTIVE)) {
            boolean offered = selling.contains(branch.getId());
            BranchMenuItemEntity entry = branchMenuItemRepository.findByBranchIdAndMenuItemId(branch.getId(), menuItemId).orElse(null);
            // Selling with no price or stock override needs no row; only create one to record "not sold here"
            if (entry == null && offered) {
                continue;
            }
            if (entry == null) {
                entry = entryFor(branch.getId(), menuItemId);
            }
            entry.setOffered(offered);
            branchMenuItemRepository.save(entry);
        }
        log.info("{} is now sold at {} branch(es), set by staff {}", item.getName(), selling.size(), staffId);
        return getDishBranches(menuItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DishExclusionResponse> getExclusions() {
        return branchMenuItemRepository.findByOfferedFalse().stream()
            .map(e -> new DishExclusionResponse(e.getMenuItemId(), e.getBranchId()))
            .toList();
    }

    private void checkBranch(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        if (!branchRepository.existsById(branchId)) {
            throw new AppException(ErrorCode.BRANCH_NOT_FOUND);
        }
    }

    private MenuItemEntity findDish(Integer menuItemId) {
        return menuItemRepository.findById(menuItemId)
            .filter(item -> Boolean.TRUE.equals(item.getIsActive()) && item.getDeletedAt() == null)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Dish not on the chain menu: " + menuItemId));
    }

    private BranchMenuItemEntity entryFor(Integer branchId, Integer menuItemId) {
        return branchMenuItemRepository.findByBranchIdAndMenuItemId(branchId, menuItemId).orElseGet(() -> {
            BranchMenuItemEntity created = new BranchMenuItemEntity();
            created.setBranchId(branchId);
            created.setMenuItemId(menuItemId);
            created.setIsAvailable(true);
            return created;
        });
    }

    private static BranchMenuItemResponse withStock(BranchMenuItemResponse response, boolean outOfStock) {
        response.setOutOfStock(outOfStock);
        if (outOfStock) {
            response.setAvailable(false);
        }
        return response;
    }

    private static BranchMenuItemResponse toResponse(MenuItemEntity item, BranchMenuItemEntity override) {
        BigDecimal branchPrice = override != null ? override.getPrice() : null;
        boolean available = override == null || Boolean.TRUE.equals(override.getIsAvailable());
        return new BranchMenuItemResponse(item.getId(), item.getCategoryId(), item.getCode(), item.getName(), item.getUnit(),
            item.getBasePrice(), branchPrice, branchPrice != null ? branchPrice : item.getBasePrice(), available, item.getImageUrl(),
            !Boolean.FALSE.equals(item.getNeedsKitchen()), false);
    }
}
