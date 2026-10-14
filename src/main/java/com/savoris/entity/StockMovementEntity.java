package com.savoris.entity;

import com.savoris.constant.StockMovementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = {
    @Index(columnList = "branch_id, ingredient_id, created_at"),
    @Index(columnList = "type, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 12, scale = 0)
    private BigDecimal unitCost;

    @Column(name = "purchase_order_id")
    private Integer purchaseOrderId;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "related_branch_id")
    private Integer relatedBranchId;

    @Column(name = "stock_count_id")
    private Integer stockCountId;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private Integer createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
