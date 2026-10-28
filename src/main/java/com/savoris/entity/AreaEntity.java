package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "areas", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AreaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "floor_name", nullable = false, length = 100)
    private String floorName;

    @Column(name = "floor_sort_order", nullable = false)
    @Builder.Default
    private Integer floorSortOrder = 1;

    @PrePersist
    void applyFloorDefaults() {
        if (floorName == null || floorName.isBlank()) floorName = name;
        if (floorSortOrder == null) floorSortOrder = 1;
    }
}
