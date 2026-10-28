package com.savoris.entity;

import com.savoris.constant.TableStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "dining_tables", uniqueConstraints = @UniqueConstraint(columnNames = {"area_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "area_id", nullable = false)
    private Integer areaId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private Integer capacity = 4;

    @Column(name = "position_x", nullable = false)
    @Builder.Default
    private Integer positionX = 0;

    @Column(name = "position_y", nullable = false)
    @Builder.Default
    private Integer positionY = 0;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TableStatus status = TableStatus.AVAILABLE;

    // Printed in the table's QR code; guests who scan it order straight to this table
    @Column(name = "qr_token", nullable = false, length = 64, unique = true)
    private String qrToken;

    @PrePersist
    void assignQrToken() {
        if (qrToken == null) {
            qrToken = UUID.randomUUID().toString().replace("-", "");
        }
    }
}
