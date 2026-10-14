package com.savoris.entity;

import com.savoris.constant.DiscountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 30, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 0)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 12, scale = 0)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
