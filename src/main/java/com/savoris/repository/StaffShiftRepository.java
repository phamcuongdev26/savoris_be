package com.savoris.repository;

import com.savoris.entity.StaffShiftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StaffShiftRepository extends JpaRepository<StaffShiftEntity, Integer> {

    @Query("select ss from StaffShiftEntity ss join ShiftEntity s on s.id = ss.shiftId "
        + "where s.branchId = :branchId and ss.workDate between :from and :to order by ss.workDate, s.startTime, ss.staffId")
    List<StaffShiftEntity> findByBranchAndDates(@Param("branchId") Integer branchId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<StaffShiftEntity> findByStaffIdAndWorkDateBetweenOrderByWorkDate(Integer staffId, LocalDate from, LocalDate to);

    boolean existsByStaffIdAndShiftIdAndWorkDate(Integer staffId, Integer shiftId, LocalDate workDate);
}
