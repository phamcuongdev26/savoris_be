package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderRequest {
    @NotNull(message = "Branch ID is required")
    private Integer branchId;

    @NotNull(message = "Supplier ID is required")
    private Integer supplierId;

    private String note;

    @NotEmpty(message = "A purchase order needs at least one item")
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PurchaseOrderLineRequest")
    public static class Item {
        @NotNull(message = "Ingredient ID is required")
        private Integer ingredientId;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.001", message = "Quantity must be positive")
        private BigDecimal quantity;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0", message = "Unit price must not be negative")
        private BigDecimal unitPrice;
    }
}
