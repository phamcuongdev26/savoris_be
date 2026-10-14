package com.savoris.repository;

import com.savoris.constant.TableStatus;
import com.savoris.entity.DiningTableEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiningTableRepository extends JpaRepository<DiningTableEntity, Integer> {

    @Query("select t from DiningTableEntity t join AreaEntity a on a.id = t.areaId where t.id = :tableId and a.branchId = :branchId")
    Optional<DiningTableEntity> findInBranch(@Param("tableId") Integer tableId, @Param("branchId") Integer branchId);

    @Query("select t from DiningTableEntity t join AreaEntity a on a.id = t.areaId where a.branchId = :branchId order by a.name, t.name")
    List<DiningTableEntity> findByBranchId(@Param("branchId") Integer branchId);

    boolean existsByAreaId(Integer areaId);

    // Table names are unique per branch, whatever area they sit in; exceptId skips the table being renamed
    @Query("select count(t) > 0 from DiningTableEntity t join AreaEntity a on a.id = t.areaId "
        + "where a.branchId = :branchId and lower(t.name) = lower(:name) and t.id <> :exceptId")
    boolean existsNameInBranch(@Param("branchId") Integer branchId, @Param("name") String name, @Param("exceptId") Integer exceptId);

    Optional<DiningTableEntity> findByQrToken(String qrToken);

    // Two phones at the same table ordering at once must end up on one bill
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTableEntity t where t.qrToken = :qrToken")
    Optional<DiningTableEntity> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DiningTableEntity t set t.status = :status where t.id = :tableId")
    int updateStatus(@Param("tableId") Integer tableId, @Param("status") TableStatus status);
}
