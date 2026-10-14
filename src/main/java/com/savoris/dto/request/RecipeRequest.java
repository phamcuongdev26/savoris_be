package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

// The full recipe of one dish (per portion); it replaces the previous one
public record RecipeRequest(@NotNull @Valid List<Line> lines) {
    public record Line(
        @NotNull(message = "Ingredient ID is required") Integer ingredientId,
        @NotNull(message = "Quantity is required") @DecimalMin(value = "0.001", message = "Quantity must be positive") BigDecimal quantity
    ) {
    }
}
