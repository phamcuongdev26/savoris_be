package com.savoris.service.impl;

import com.savoris.dto.request.CreateMenuCategoryRequest;
import com.savoris.dto.request.UpdateMenuCategoryRequest;
import com.savoris.dto.response.MenuCategoryResponse;
import com.savoris.entity.MenuCategoryEntity;
import com.savoris.repository.MenuCategoryRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.service.MenuCategoryService;
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
public class MenuCategoryServiceImpl implements MenuCategoryService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;

    @Override
    @Transactional
    public MenuCategoryResponse createCategory(CreateMenuCategoryRequest request) {
        MenuCategoryEntity category = MenuCategoryEntity.builder()
            .name(request.getName())
            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        MenuCategoryEntity saved = categoryRepository.save(category);
        log.info("Created menu category: {}", saved.getName());
        return MenuCategoryResponse.from(saved);
    }

    @Override
    public MenuCategoryResponse getCategory(Integer categoryId) {
        MenuCategoryEntity category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found: " + categoryId));
        return MenuCategoryResponse.from(category);
    }

    @Override
    @Transactional
    public MenuCategoryResponse updateCategory(Integer categoryId, UpdateMenuCategoryRequest request) {
        MenuCategoryEntity category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found: " + categoryId));

        category.setName(request.getName());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        MenuCategoryEntity updated = categoryRepository.save(category);
        log.info("Updated menu category: {}", updated.getName());
        return MenuCategoryResponse.from(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer categoryId) {
        MenuCategoryEntity category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Category not found: " + categoryId));

        if (itemRepository.existsByCategoryIdAndIsActiveTrueAndDeletedAtIsNull(categoryId)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Hãy chuyển hoặc ẩn các món trong danh mục trước khi xóa danh mục");
        }

        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Deleted menu category: {}", category.getName());
    }


    @Override
    public List<MenuCategoryResponse> searchCategories(String name) {
        return categoryRepository.findByNameContainingIgnoreCase(name)
            .stream()
            .map(MenuCategoryResponse::from)
            .toList();
    }
}
