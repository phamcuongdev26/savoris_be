package com.savoris.entity;

import com.savoris.constant.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deliveries", indexes = @Index(columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Integer orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.WAITING;

    @Column(length = 50)
    private String provider;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "shipper_staff_id")
    private Integer shipperStaffId;

    @Column(name = "shipper_name", length = 255)
    private String shipperName;

    @Column(name = "shipper_phone", length = 20)
    private String shipperPhone;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "failed_reason", length = 255)
    private String failedReason;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
