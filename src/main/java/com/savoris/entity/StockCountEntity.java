package com.savoris.entity;

import com.savoris.constant.StockCountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_counts", indexes = @Index(columnList = "branch_id, counted_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockCountStatus status = StockCountStatus.DRAFT;

    @Column(name = "counted_at", nullable = false)
    private LocalDateTime countedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "completed_by")
    private Integer completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
