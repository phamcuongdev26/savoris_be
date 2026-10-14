package com.savoris.repository;

import com.savoris.entity.BranchInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventoryEntity, Integer> {
    Optional<BranchInventoryEntity> findByBranchIdAndIngredientId(Integer branchId, Integer ingredientId);
    List<BranchInventoryEntity> findByBranchId(Integer branchId);

    // Ingredients at or below their minimum stock level
    @Query("select bi from BranchInventoryEntity bi join IngredientEntity i on i.id = bi.ingredientId "
        + "where bi.branchId = :branchId and bi.quantity <= i.minStock order by bi.ingredientId")
    List<BranchInventoryEntity> findLowStock(@Param("branchId") Integer branchId);

    // Creates the stock row of a newly stocked ingredient; a concurrent duplicate is ignored
    @Modifying
    @Query(value = "INSERT INTO branch_inventories (branch_id, ingredient_id, quantity, updated_at) "
        + "VALUES (:branchId, :ingredientId, 0, :now) ON CONFLICT DO NOTHING", nativeQuery = true)
    void createIfAbsent(@Param("branchId") Integer branchId, @Param("ingredientId") Integer ingredientId,
                        @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BranchInventoryEntity bi set bi.quantity = bi.quantity + :amount, bi.updatedAt = :now "
        + "where bi.branchId = :branchId and bi.ingredientId = :ingredientId")
    int add(@Param("branchId") Integer branchId, @Param("ingredientId") Integer ingredientId,
            @Param("amount") BigDecimal amount, @Param("now") LocalDateTime now);

    // Atomic so two dishes cooked at the same moment both get deducted
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BranchInventoryEntity bi set bi.quantity = bi.quantity - :amount, bi.updatedAt = :now "
        + "where bi.branchId = :branchId and bi.ingredientId = :ingredientId")
    int deduct(@Param("branchId") Integer branchId, @Param("ingredientId") Integer ingredientId,
               @Param("amount") BigDecimal amount, @Param("now") LocalDateTime now);
}
