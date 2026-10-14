package com.savoris.dto.response;

import com.savoris.constant.PurchaseOrderStatus;
import com.savoris.entity.IngredientEntity;
import com.savoris.entity.PurchaseOrderEntity;
import com.savoris.entity.PurchaseOrderItemEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {
    private Integer id;
    private String code;
    private Integer branchId;
    private Integer supplierId;
    private PurchaseOrderStatus status;
    private BigDecimal totalAmount;
    private String note;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime orderedAt;
    private LocalDateTime receivedAt;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "PurchaseOrderLine")
    public static class Item {
        private Integer id;
        private Integer ingredientId;
        private String ingredientName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal receivedQuantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
    }

    public static PurchaseOrderResponse from(PurchaseOrderEntity order, List<PurchaseOrderItemEntity> items,
                                             Map<Integer, IngredientEntity> ingredients) {
        List<Item> lines = items.stream().map(i -> {
            IngredientEntity ingredient = ingredients.get(i.getIngredientId());
            BigDecimal quantity = i.getReceivedQuantity() != null ? i.getReceivedQuantity() : i.getQuantity();
            return new Item(i.getId(), i.getIngredientId(), ingredient != null ? ingredient.getName() : null,
                ingredient != null ? ingredient.getUnit() : null, i.getQuantity(), i.getReceivedQuantity(), i.getUnitPrice(),
                quantity.multiply(i.getUnitPrice()).setScale(0, RoundingMode.HALF_UP));
        }).toList();
        return new PurchaseOrderResponse(order.getId(), order.getCode(), order.getBranchId(), order.getSupplierId(),
            order.getStatus(), order.getTotalAmount(), order.getNote(), order.getCreatedBy(), order.getCreatedAt(),
            order.getOrderedAt(), order.getReceivedAt(), lines);
    }
}
