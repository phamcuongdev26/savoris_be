package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_count_items", uniqueConstraints = @UniqueConstraint(columnNames = {"stock_count_id", "ingredient_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "stock_count_id", nullable = false)
    private Integer stockCountId;

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId;

    @Column(name = "system_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal difference;

    @Column(length = 255)
    private String note;
}
