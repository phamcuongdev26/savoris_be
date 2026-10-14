package com.savoris.entity;

import com.savoris.constant.OrderItemStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", indexes = {
    @Index(columnList = "order_id"),
    @Index(columnList = "status"),
    @Index(columnList = "ready_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "menu_item_id", nullable = false)
    private Integer menuItemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus status = OrderItemStatus.PENDING;

    @Column(length = 255)
    private String note;

    @Column(name = "served_by")
    private Integer servedBy;

    @Column(name = "cooked_by")
    private Integer cookedBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // Copied from the dish when ordered: false = handed out by the floor staff, never on the kitchen screen
    @Builder.Default
    @Column(name = "needs_kitchen", nullable = false)
    private Boolean needsKitchen = true;

    @CreationTimestamp
    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    // When the kitchen finished the dish; used to measure kitchen throughput
    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    // Ordered by the guest through the table QR, not by a member of staff
    @Column(name = "ordered_by_guest", nullable = false)
    private Boolean orderedByGuest = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
