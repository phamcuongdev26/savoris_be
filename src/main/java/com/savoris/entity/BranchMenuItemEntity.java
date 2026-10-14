package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "branch_menu_items", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "menu_item_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchMenuItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "menu_item_id", nullable = false)
    private Integer menuItemId;

    @Column(name = "price", precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    // false = this branch does not sell the dish at all (chosen by the owner)
    @Column(name = "offered", nullable = false)
    private Boolean offered = true;
}
