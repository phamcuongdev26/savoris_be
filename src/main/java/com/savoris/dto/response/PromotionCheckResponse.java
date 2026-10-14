package com.savoris.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCheckResponse {
    private String code;
    private String name;
    private BigDecimal orderAmount;
    private BigDecimal discount;
}
