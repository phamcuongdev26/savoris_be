package com.savoris.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * The chain menu shown on the guests' home page before they scan a table: chain prices,
 * since the branch (and its own prices and sold-out dishes) is only known after the scan.
 */
public record PublicMenuResponse(List<GuestTableResponse.Category> categories, List<Dish> dishes, BigDecimal vatPercent) {

    @Schema(name = "PublicMenuDish")
    public record Dish(Integer id, Integer categoryId, String name, String description, String imageUrl,
                       String unit, BigDecimal price) {
    }
}
