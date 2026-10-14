package com.savoris.dto.response;

import com.savoris.entity.MenuItemEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {
    private Integer id;
    private Integer categoryId;
    private String code;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal basePrice;
    private String unit;
    private Boolean isActive;
    private Boolean needsKitchen;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MenuItemResponse from(MenuItemEntity entity) {
        MenuItemResponse response = new MenuItemResponse();
        response.setId(entity.getId());
        response.setCategoryId(entity.getCategoryId());
        response.setCode(entity.getCode());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setImageUrl(entity.getImageUrl());
        response.setBasePrice(entity.getBasePrice());
        response.setUnit(entity.getUnit());
        response.setIsActive(entity.getIsActive());
        response.setNeedsKitchen(entity.getNeedsKitchen());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
