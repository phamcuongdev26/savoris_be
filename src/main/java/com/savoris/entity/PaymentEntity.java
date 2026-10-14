package com.savoris.entity;

import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(columnList = "order_id"),
    @Index(columnList = "method, paid_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // One payment per order: a second attempt is rejected even if two cashiers press "pay" at the same moment
    @Column(name = "order_id", nullable = false, unique = true)
    private Integer orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    // Order total actually kept by the restaurant
    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "cash_received", precision = 14, scale = 0)
    private BigDecimal cashReceived;

    @Column(name = "change_amount", precision = 14, scale = 0)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PAID;

    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    // null when the bank confirmed the transfer through the SePay webhook
    @Column(name = "received_by")
    private Integer receivedBy;

    @Column(name = "refunded_by")
    private Integer refundedBy;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @CreationTimestamp
    @Column(name = "paid_at", nullable = false, updatable = false)
    private LocalDateTime paidAt;
}
