package com.savoris.repository;

import com.savoris.constant.StaffStatus;
import com.savoris.entity.StaffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<StaffEntity, Integer> {
    Optional<StaffEntity> findByUsername(String username);
    Optional<StaffEntity> findByEmail(String email);
    Optional<StaffEntity> findByCode(String code);
    List<StaffEntity> findByBranchId(Integer branchId);
    List<StaffEntity> findByBranchIdAndStatus(Integer branchId, StaffStatus status);
    List<StaffEntity> findByFullNameContainingIgnoreCase(String fullName);
    long countByBranchIdAndRole(Integer branchId, String role);
}
