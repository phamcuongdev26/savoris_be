package com.savoris.entity;

import com.savoris.constant.OrderStatus;
import com.savoris.constant.OrderType;
import com.savoris.constant.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
    @Index(columnList = "branch_id, opened_at"),
    @Index(columnList = "branch_id, status"),
    @Index(columnList = "table_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Column(name = "table_id")
    private Integer tableId;

    @Column(name = "reservation_id")
    private Integer reservationId;

    @Column(name = "guest_count")
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "delivery_phone", length = 20)
    private String deliveryPhone;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 0)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // null = placed by the guest through the table QR code
    @Column(name = "opened_by")
    private Integer openedBy;

    @Column(name = "closed_by")
    private Integer closedBy;

    // Web takeaway: who ordered, how they will pay, and the key of their tracking link
    @Column(name = "guest_name", length = 100)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "pickup_at")
    private LocalDateTime pickupAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", length = 20)
    private PaymentMethod payMethod;

    @Column(name = "public_token", length = 64, unique = true)
    private String publicToken;

    // When the guest tapped "I have transferred"; the order then waits for the cashier instead of expiring
    @Column(name = "transfer_claimed_at")
    private LocalDateTime transferClaimedAt;

    // Cancelled although the guest said they transferred, or the bank reported money after it expired
    @Column(name = "refund_due_at")
    private LocalDateTime refundDueAt;

    @Column(name = "refund_settled_at")
    private LocalDateTime refundSettledAt;

    @Column(name = "refund_settled_by")
    private Integer refundSettledBy;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
