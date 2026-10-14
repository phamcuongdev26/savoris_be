package com.savoris.service;

import com.savoris.dto.request.CreateMenuItemRequest;
import com.savoris.dto.request.UpdateMenuItemRequest;
import com.savoris.dto.response.MenuItemResponse;

import java.util.List;

public interface MenuItemService {
    MenuItemResponse createItem(CreateMenuItemRequest request);
    MenuItemResponse getItem(Integer itemId);
    MenuItemResponse updateItem(Integer itemId, UpdateMenuItemRequest request);
    void deleteItem(Integer itemId);
    List<MenuItemResponse> getItemsByCategory(Integer categoryId);
    List<MenuItemResponse> searchItems(String name);
}
