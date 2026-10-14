package com.savoris.repository;

import com.savoris.entity.CodeSequenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CodeSequenceRepository extends JpaRepository<CodeSequenceEntity, Integer> {

    // Creates today's counter row if missing; a concurrent duplicate is silently ignored
    @Modifying
    @Query(value = "INSERT INTO code_sequences (branch_id, type, seq_date, last_value) "
        + "VALUES (:branchId, :type, :seqDate, 0) ON CONFLICT DO NOTHING", nativeQuery = true)
    void createIfAbsent(@Param("branchId") Integer branchId, @Param("type") String type,
                        @Param("seqDate") LocalDateTime seqDate);

    // Row lock is held until the calling transaction commits, so concurrent orders get distinct values
    @Modifying
    @Query(value = "UPDATE code_sequences SET last_value = last_value + 1 "
        + "WHERE branch_id = :branchId AND type = :type AND seq_date = :seqDate", nativeQuery = true)
    int increment(@Param("branchId") Integer branchId, @Param("type") String type,
                  @Param("seqDate") LocalDateTime seqDate);

    @Query(value = "SELECT last_value FROM code_sequences "
        + "WHERE branch_id = :branchId AND type = :type AND seq_date = :seqDate", nativeQuery = true)
    int findLastValue(@Param("branchId") Integer branchId, @Param("type") String type,
                      @Param("seqDate") LocalDateTime seqDate);
}
