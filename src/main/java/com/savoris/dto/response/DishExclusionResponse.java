package com.savoris.dto.response;

// A dish a branch does not sell
public record DishExclusionResponse(Integer menuItemId, Integer branchId) {
}
