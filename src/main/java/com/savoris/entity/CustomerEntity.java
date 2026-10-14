package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 255)
    private String fullName;

    @Column(unique = true)
    private String email;

    @Column(name = "birth_date")
    private LocalDateTime birthDate;

    @Column(nullable = false)
    private Integer points = 0;

    @Column(name = "total_spent", nullable = false, precision = 14, scale = 0)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
