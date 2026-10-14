package com.savoris.dto.response;

import com.savoris.entity.BranchInventoryEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private Integer id;
    private Integer branchId;
    private Integer ingredientId;
    private BigDecimal quantity;
    private LocalDateTime updatedAt;

    public static InventoryResponse from(BranchInventoryEntity entity) {
        return new InventoryResponse(entity.getId(), entity.getBranchId(), entity.getIngredientId(), entity.getQuantity(), entity.getUpdatedAt());
    }
}
