package com.savoris.repository;

import com.savoris.entity.BranchMenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchMenuItemRepository extends JpaRepository<BranchMenuItemEntity, Integer> {
    List<BranchMenuItemEntity> findByBranchIdAndMenuItemIdIn(Integer branchId, Collection<Integer> menuItemIds);
    List<BranchMenuItemEntity> findByBranchId(Integer branchId);
    List<BranchMenuItemEntity> findByMenuItemId(Integer menuItemId);
    List<BranchMenuItemEntity> findByOfferedFalse();
    Optional<BranchMenuItemEntity> findByBranchIdAndMenuItemId(Integer branchId, Integer menuItemId);
}
