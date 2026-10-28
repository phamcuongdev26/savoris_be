package com.savoris.entity;

import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.VoidResolution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_item_void_history", indexes = {
    @Index(columnList = "order_item_id, requested_at")
})
@Getter
@Setter
@NoArgsConstructor
public class OrderItemVoidHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "order_item_id", nullable = false)
    private Integer orderItemId;

    @Column(name = "requested_by")
    private Integer requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "request_reason", length = 255)
    private String requestReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private OrderItemStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VoidResolution resolution;

    @Column(name = "resolved_by")
    private Integer resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
