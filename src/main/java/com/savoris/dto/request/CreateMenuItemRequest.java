package com.savoris.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuItemRequest {
    @NotBlank(message = "Item code is required")
    private String code;

    @NotBlank(message = "Item name is required")
    private String name;

    private String description;

    @NotNull(message = "Category ID is required")
    private Integer categoryId;

    @NotNull(message = "Base price is required")
    @DecimalMin("0")
    private BigDecimal basePrice;

    private String imageUrl;
    private String unit = "phần";
    private Boolean isActive = true;

    // false = drink / ready-made: no kitchen, staff hand it out and mark it sold out
    private Boolean needsKitchen;
}
