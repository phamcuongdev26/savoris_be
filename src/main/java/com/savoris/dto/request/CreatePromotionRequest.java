package com.savoris.dto.request;

import com.savoris.constant.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePromotionRequest {
    @NotBlank(message = "Code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code may only contain letters, digits, - and _")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    // Percent (1-100) for PERCENT, amount in VND for FIXED_AMOUNT
    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "1", message = "Discount value must be positive")
    private BigDecimal discountValue;

    // Cap for PERCENT discounts, e.g. 10% but at most 50.000
    @DecimalMin("1")
    private BigDecimal maxDiscount;

    @DecimalMin("0")
    private BigDecimal minOrderAmount;

    @NotNull(message = "Start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "End time is required")
    private LocalDateTime endAt;

    // null = unlimited
    @Positive
    private Integer usageLimit;

    // Empty or null = every branch of the chain
    private List<Integer> branchIds;
}
