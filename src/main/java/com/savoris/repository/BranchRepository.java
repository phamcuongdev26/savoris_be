package com.savoris.repository;

import com.savoris.constant.BranchStatus;
import com.savoris.entity.BranchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<BranchEntity, Integer> {
    Optional<BranchEntity> findByCode(String code);
    List<BranchEntity> findByStatus(BranchStatus status);
    List<BranchEntity> findByNameContainingIgnoreCase(String name);

    // Serializes the "is the kitchen full?" check of a branch so concurrent takeaway orders cannot overshoot capacity
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BranchEntity b where b.id = :id")
    Optional<BranchEntity> findByIdForUpdate(@Param("id") Integer id);
}
