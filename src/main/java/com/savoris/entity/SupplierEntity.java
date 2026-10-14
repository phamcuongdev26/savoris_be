package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
