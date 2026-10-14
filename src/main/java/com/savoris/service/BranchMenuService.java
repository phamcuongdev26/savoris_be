package com.savoris.service;

import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.dto.response.DishBranchResponse;
import com.savoris.dto.response.DishExclusionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface BranchMenuService {
    List<BranchMenuItemResponse> getMenu(Integer staffId, Integer branchId);
    // The same menu for guests ordering from the table QR code (no staff account)
    List<BranchMenuItemResponse> getPublicMenu(Integer branchId);
    BranchMenuItemResponse setAvailability(Integer staffId, Integer branchId, Integer menuItemId, boolean available);
    BranchMenuItemResponse setPrice(Integer staffId, Integer branchId, Integer menuItemId, BigDecimal price);
    List<DishBranchResponse> getDishBranches(Integer menuItemId);
    List<DishBranchResponse> setDishBranches(Integer staffId, Integer menuItemId, List<Integer> branchIds);
    List<DishExclusionResponse> getExclusions();
}
