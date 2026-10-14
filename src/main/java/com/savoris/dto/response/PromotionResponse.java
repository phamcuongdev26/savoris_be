package com.savoris.dto.response;

import com.savoris.constant.DiscountType;
import com.savoris.entity.PromotionEntity;
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
public class PromotionResponse {
    private Integer id;
    private String code;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer usageLimit;
    private Integer usedCount;
    private Boolean isActive;
    // Empty = every branch
    private List<Integer> branchIds;

    public static PromotionResponse from(PromotionEntity entity, List<Integer> branchIds) {
        return new PromotionResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDiscountType(),
            entity.getDiscountValue(), entity.getMaxDiscount(), entity.getMinOrderAmount(), entity.getStartAt(),
            entity.getEndAt(), entity.getUsageLimit(), entity.getUsedCount(), entity.getIsActive(), branchIds);
    }
}
