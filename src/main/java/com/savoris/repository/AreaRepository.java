package com.savoris.repository;

import com.savoris.entity.AreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<AreaEntity, Integer> {
    List<AreaEntity> findByBranchIdOrderByName(Integer branchId);

    Optional<AreaEntity> findByBranchIdAndName(Integer branchId, String name);
}
