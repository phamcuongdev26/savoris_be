package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "recipes", uniqueConstraints = @UniqueConstraint(columnNames = {"menu_item_id", "ingredient_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "menu_item_id", nullable = false)
    private Integer menuItemId;

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;
}
