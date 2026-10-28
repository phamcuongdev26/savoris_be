package com.savoris.dto.response;

import com.savoris.constant.OrderItemStatus;
import com.savoris.entity.OrderItemEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Integer id;
    private Integer orderId;
    private Integer menuItemId;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal amount;
    private OrderItemStatus status;
    private String notes;
    private String cancelReason;
    // When the kitchen finished it (ready-made: when ordered), to remind the cashier of food waiting to be collected
    private Instant readyAt;
    // The guest ordered it from the table QR: the floor staff get a heads-up
    private boolean orderedByGuest;

    public static OrderItemResponse from(OrderItemEntity entity) {
        return new OrderItemResponse(entity.getId(), entity.getOrderId(), entity.getMenuItemId(), entity.getItemName(),
            entity.getUnitPrice(), entity.getQuantity(), entity.getAmount(), entity.getStatus(), entity.getNote(),
            entity.getCancelReason(), entity.getReadyAt() == null ? null
                : entity.getReadyAt().atZone(ZoneId.systemDefault()).toInstant(),
            Boolean.TRUE.equals(entity.getOrderedByGuest()));
    }
}
