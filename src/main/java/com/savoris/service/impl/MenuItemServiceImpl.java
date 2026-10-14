package com.savoris.service.impl;

import com.savoris.dto.request.CreateMenuItemRequest;
import com.savoris.dto.request.UpdateMenuItemRequest;
import com.savoris.dto.response.MenuItemResponse;
import com.savoris.entity.MenuItemEntity;
import com.savoris.repository.MenuItemRepository;
import com.savoris.service.MenuItemService;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository itemRepository;

    @Override
    @Transactional
    public MenuItemResponse createItem(CreateMenuItemRequest request) {
        if (itemRepository.findByCode(request.getCode()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_CODE, "Item code already exists: " + request.getCode());
        }

        MenuItemEntity item = MenuItemEntity.builder()
            .code(request.getCode())
            .name(request.getName())
            .description(request.getDescription())
            .categoryId(request.getCategoryId())
            .basePrice(request.getBasePrice())
            .imageUrl(request.getImageUrl())
            .unit(request.getUnit() != null ? request.getUnit() : "phần")
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .needsKitchen(request.getNeedsKitchen() != null ? request.getNeedsKitchen() : true)
            .build();

        MenuItemEntity saved = itemRepository.save(item);
        log.info("Created menu item: {} ({})", saved.getName(), saved.getCode());
        return MenuItemResponse.from(saved);
    }

    @Override
    public MenuItemResponse getItem(Integer itemId) {
        MenuItemEntity item = itemRepository.findById(itemId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item not found: " + itemId));
        return MenuItemResponse.from(item);
    }

    @Override
    @Transactional
    public MenuItemResponse updateItem(Integer itemId, UpdateMenuItemRequest request) {
        MenuItemEntity item = itemRepository.findById(itemId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item not found: " + itemId));

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        if (request.getBasePrice() != null) {
            item.setBasePrice(request.getBasePrice());
        }
        if (request.getImageUrl() != null) {
            item.setImageUrl(request.getImageUrl());
        }
        if (request.getUnit() != null) {
            item.setUnit(request.getUnit());
        }
        if (request.getIsActive() != null) {
            item.setIsActive(request.getIsActive());
        }
        if (request.getNeedsKitchen() != null) {
            item.setNeedsKitchen(request.getNeedsKitchen());
        }

        MenuItemEntity updated = itemRepository.save(item);
        log.info("Updated menu item: {} ({})", updated.getName(), updated.getCode());
        return MenuItemResponse.from(updated);
    }

    @Override
    @Transactional
    public void deleteItem(Integer itemId) {
        MenuItemEntity item = itemRepository.findById(itemId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Item not found: " + itemId));

        itemRepository.delete(item);
        log.info("Deleted menu item: {} ({})", item.getName(), item.getCode());
    }

    @Override
    public List<MenuItemResponse> getItemsByCategory(Integer categoryId) {
        return itemRepository.findByCategoryId(categoryId)
            .stream()
            .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
            .map(MenuItemResponse::from)
            .toList();
    }

    @Override
    public List<MenuItemResponse> searchItems(String name) {
        return itemRepository.findByNameContainingIgnoreCase(name)
            .stream()
            .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
            .map(MenuItemResponse::from)
            .toList();
    }
}
