package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * What actually arrived. Items left out (or no body at all) are taken as delivered in full.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceivePurchaseOrderRequest {
    @Valid
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReceivedLine")
    public static class Item {
        @NotNull(message = "Purchase order item ID is required")
        private Integer itemId;

        @NotNull(message = "Received quantity is required")
        @DecimalMin(value = "0", message = "Received quantity must not be negative")
        private BigDecimal receivedQuantity;
    }
}
