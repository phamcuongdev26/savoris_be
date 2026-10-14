package com.savoris.repository;

import com.savoris.entity.PromotionBranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionBranchRepository extends JpaRepository<PromotionBranchEntity, Integer> {
    List<PromotionBranchEntity> findByPromotionId(Integer promotionId);
    void deleteByPromotionId(Integer promotionId);
}
