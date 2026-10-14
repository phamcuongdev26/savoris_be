package com.savoris.dto.response;

import com.savoris.entity.AreaEntity;

public record AreaResponse(Integer id, Integer branchId, String name) {
    public static AreaResponse from(AreaEntity entity) {
        return new AreaResponse(entity.getId(), entity.getBranchId(), entity.getName());
    }
}
