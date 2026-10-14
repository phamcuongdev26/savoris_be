package com.savoris.dto.response;

import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.OrderType;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.OrderItemEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KitchenTicketResponse {
    private Integer itemId;
    private Integer orderId;
    private String orderCode;
    private OrderType orderType;
    private Integer tableId;
    private Integer menuItemId;
    private String itemName;
    private Integer quantity;
    private String note;
    private OrderItemStatus status;
    private Integer cookedBy;
    private LocalDateTime orderedAt;
    private long waitingMinutes;

    public static KitchenTicketResponse from(OrderItemEntity item, OrderEntity order, LocalDateTime now) {
        return new KitchenTicketResponse(
            item.getId(),
            order.getId(),
            order.getCode(),
            order.getType(),
            order.getTableId(),
            item.getMenuItemId(),
            item.getItemName(),
            item.getQuantity(),
            item.getNote(),
            item.getStatus(),
            item.getCookedBy(),
            item.getOrderedAt(),
            Duration.between(item.getOrderedAt(), now).toMinutes()
        );
    }
}
