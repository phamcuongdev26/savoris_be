package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items", uniqueConstraints = @UniqueConstraint(columnNames = {"purchase_order_id", "ingredient_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "purchase_order_id", nullable = false)
    private Integer purchaseOrderId;

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "received_quantity", precision = 12, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;
}
