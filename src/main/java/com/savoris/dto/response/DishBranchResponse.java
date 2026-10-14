package com.savoris.dto.response;

// One branch and whether it sells a given dish
public record DishBranchResponse(Integer branchId, String code, String name, boolean offered) {
}
