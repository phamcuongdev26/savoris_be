package com.savoris.repository;

import com.savoris.entity.PromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<PromotionEntity, Integer> {
    Optional<PromotionEntity> findByCodeIgnoreCase(String code);
    List<PromotionEntity> findAllByOrderByStartAtDesc();

    // Takes one use of the code only while uses remain, so a limited code is never over-used at peak hours
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PromotionEntity p set p.usedCount = p.usedCount + 1 "
        + "where p.id = :id and (p.usageLimit is null or p.usedCount < p.usageLimit)")
    int takeUse(@Param("id") Integer id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PromotionEntity p set p.usedCount = p.usedCount - 1 where p.id = :id and p.usedCount > 0")
    int releaseUse(@Param("id") Integer id);
}
