package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promotion_branches", uniqueConstraints = @UniqueConstraint(columnNames = {"promotion_id", "branch_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionBranchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "promotion_id", nullable = false)
    private Integer promotionId;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;
}
