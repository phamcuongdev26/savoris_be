package com.savoris.repository;

import com.savoris.entity.ShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<ShiftEntity, Integer> {
    List<ShiftEntity> findByBranchIdOrderByStartTime(Integer branchId);
}
