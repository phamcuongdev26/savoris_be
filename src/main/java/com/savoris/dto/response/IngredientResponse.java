package com.savoris.dto.response;

import com.savoris.entity.IngredientEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngredientResponse {
    private Integer id;
    private String code;
    private String name;
    private String unit;
    private BigDecimal minStock;

    public static IngredientResponse from(IngredientEntity entity) {
        return new IngredientResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getUnit(), entity.getMinStock());
    }
}
