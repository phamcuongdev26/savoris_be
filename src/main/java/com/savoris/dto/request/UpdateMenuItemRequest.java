package com.savoris.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuItemRequest {
    @NotBlank(message = "Item name is required")
    private String name;

    private String description;

    @Positive(message = "Category ID must be positive")
    private Integer categoryId;

    @DecimalMin("0")
    private BigDecimal basePrice;

    private String imageUrl;
    private String unit;
    private Boolean isActive;
    private Boolean takeawayAvailable;

    // false = drink / ready-made: no kitchen, staff hand it out and mark it sold out
    private Boolean needsKitchen;
}
