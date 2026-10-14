package com.savoris.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a guest sees after scanning the QR on a table: where they are and what they can order.
 */
public record GuestTableResponse(String branchCode, String branchName, String branchAddress, String areaName,
                                 String tableName, Integer capacity, BigDecimal vatPercent, List<Category> categories,
                                 List<BranchMenuItemResponse> menu) {

    @Schema(name = "GuestMenuCategory")
    public record Category(Integer id, String name) {
    }
}
