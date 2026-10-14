package com.savoris.dto.request;

import com.savoris.constant.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotNull(message = "Branch ID is required")
    private Integer branchId;

    @NotNull(message = "Order type is required")
    private OrderType orderType;

    private Integer tableId;
    private Integer customerId;

    // Optional promotion code typed in by the cashier
    private String promotionCode;

    // Loyalty points the customer wants to spend on this order (needs customerId)
    @PositiveOrZero(message = "Points must not be negative")
    private Integer pointsToUse;
    private String notes;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotNull(message = "Menu item ID is required")
        private Integer menuItemId;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        private String notes;
    }
}
