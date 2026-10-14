package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuCategoryRequest {
    @NotBlank(message = "Category name is required")
    private String name;

    private Integer sortOrder = 0;

    private Boolean isActive = true;
}
