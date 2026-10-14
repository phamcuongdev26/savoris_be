package com.savoris.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateIngredientRequest {
    @NotBlank(message = "Code is required")
    @Size(max = 30)
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    // kg, lít, quả...
    @NotBlank(message = "Unit is required")
    @Size(max = 20)
    private String unit;

    // Below this the ingredient shows up in the low-stock report
    @DecimalMin("0")
    private BigDecimal minStock;
}
