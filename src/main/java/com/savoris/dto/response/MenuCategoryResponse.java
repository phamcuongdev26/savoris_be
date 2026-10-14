package com.savoris.dto.response;

import com.savoris.entity.MenuCategoryEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuCategoryResponse {
    private Integer id;
    private String name;
    private Integer sortOrder;
    private Boolean isActive;

    public static MenuCategoryResponse from(MenuCategoryEntity entity) {
        MenuCategoryResponse response = new MenuCategoryResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setSortOrder(entity.getSortOrder());
        response.setIsActive(entity.getIsActive());
        return response;
    }
}
