package com.savoris.repository;

import com.savoris.constant.ReservationStatus;
import com.savoris.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Integer> {
    List<ReservationEntity> findByBranchIdAndStatus(Integer branchId, ReservationStatus status);
    List<ReservationEntity> findByBranchId(Integer branchId);
    List<ReservationEntity> findByReservedAtBetween(LocalDateTime start, LocalDateTime end);
    List<ReservationEntity> findByBranchIdAndCustomerPhone(Integer branchId, String customerPhone);

    /** Active (non-cancelled) reservations for a branch within a time window — used for slot capacity. */
    List<ReservationEntity> findByBranchIdAndReservedAtBetweenAndStatusNot(
            Integer branchId, LocalDateTime start, LocalDateTime end, ReservationStatus excludeStatus);
    List<ReservationEntity> findByBranchIdAndReservedAtBetweenAndStatusNotIn(
            Integer branchId, LocalDateTime start, LocalDateTime end, List<ReservationStatus> excludedStatuses);
}
