package com.savoris.entity;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;

import java.time.LocalDateTime;

@Entity
@Table(name = "staffs", indexes = @Index(columnList = "branch_id, role"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "branch_id")
    private Integer branchId;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffStatus status = StaffStatus.ACTIVE;

    @Column(name = "hourly_rate", precision = 12, scale = 0)
    private BigDecimal hourlyRate;

    @Column(name = "hired_at")
    private LocalDateTime hiredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
