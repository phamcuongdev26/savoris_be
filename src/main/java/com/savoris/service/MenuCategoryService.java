package com.savoris.service;

import com.savoris.dto.request.CreateMenuCategoryRequest;
import com.savoris.dto.request.UpdateMenuCategoryRequest;
import com.savoris.dto.response.MenuCategoryResponse;

import java.util.List;

public interface MenuCategoryService {
    MenuCategoryResponse createCategory(CreateMenuCategoryRequest request);
    MenuCategoryResponse getCategory(Integer categoryId);
    MenuCategoryResponse updateCategory(Integer categoryId, UpdateMenuCategoryRequest request);
    void deleteCategory(Integer categoryId);
    List<MenuCategoryResponse> searchCategories(String name);
}
