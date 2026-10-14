package com.savoris.dto.response;

import java.math.BigDecimal;

public record RecipeLineResponse(Integer ingredientId, String ingredientName, String unit, BigDecimal quantity) {
}
