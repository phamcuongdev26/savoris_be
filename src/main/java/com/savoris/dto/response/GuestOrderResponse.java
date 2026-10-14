package com.savoris.dto.response;

import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The table's bill as the guest sees it: dishes and their kitchen progress, no staff or customer data.
 */
public record GuestOrderResponse(String code, OrderStatus status, BigDecimal subtotal, BigDecimal discountAmount,
                                 BigDecimal vatAmount, BigDecimal totalAmount, List<Item> items) {

    @Schema(name = "GuestOrderItem")
    public record Item(String itemName, Integer quantity, BigDecimal amount, OrderItemStatus status, String notes,
                       String imageUrl) {
    }

    // images: dish photo by menu item id
    public static GuestOrderResponse from(OrderResponse order, Map<Integer, String> images) {
        return new GuestOrderResponse(order.getCode(), order.getStatus(), order.getSubtotal(), order.getDiscountAmount(),
            order.getVatAmount(), order.getTotalAmount(),
            order.getItems().stream()
                .map(i -> new Item(i.getItemName(), i.getQuantity(), i.getAmount(), i.getStatus(), i.getNotes(),
                    images.get(i.getMenuItemId())))
                .toList());
    }
}
