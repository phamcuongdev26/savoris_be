package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays = 1;

    @Column(name = "safety_days", nullable = false)
    private Integer safetyDays = 1;
}
