package com.savoris.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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

    @DecimalMin("0")
    private BigDecimal basePrice;

    private String imageUrl;
    private String unit;
    private Boolean isActive;

    // false = drink / ready-made: no kitchen, staff hand it out and mark it sold out
    private Boolean needsKitchen;
}
