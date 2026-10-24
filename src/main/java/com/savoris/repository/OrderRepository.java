package com.savoris.repository;

import com.savoris.constant.OrderStatus;
import com.savoris.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {
    List<OrderEntity> findByBranchIdAndStatus(Integer branchId, OrderStatus status);
    List<OrderEntity> findByBranchIdAndStatusInOrderByIdAsc(Integer branchId, List<OrderStatus> statuses);
    Optional<OrderEntity> findByPublicToken(String publicToken);

    Optional<OrderEntity> findByCode(String code);

    List<OrderEntity> findByBranchIdAndRefundDueAtIsNotNullAndRefundSettledAtIsNullOrderByIdAsc(Integer branchId);

    @Query("select o from OrderEntity o where o.status = com.savoris.constant.OrderStatus.AWAITING_PAYMENT "
        + "and o.publicToken is not null and o.transferClaimedAt is null and o.openedAt < :cutoff")
    List<OrderEntity> findExpiredWebTakeaways(@Param("cutoff") LocalDateTime cutoff);
    List<OrderEntity> findByBranchIdAndOpenedAtBetween(Integer branchId, LocalDateTime start, LocalDateTime end);
    List<OrderEntity> findByCustomerId(Integer customerId);
    List<OrderEntity> findByCustomerIdAndBranchId(Integer customerId, Integer branchId);
    boolean existsByTableIdAndStatus(Integer tableId, OrderStatus status);
    boolean existsByTableId(Integer tableId);
    Optional<OrderEntity> findFirstByTableIdAndStatusOrderByIdDesc(Integer tableId, OrderStatus status);

    // Rows of [branchId, orderCount]
    @Query("select o.branchId, count(o) from OrderEntity o where o.status = :status group by o.branchId")
    List<Object[]> countByStatusGroupByBranch(@Param("status") OrderStatus status);

    // Rows of [branchId, orderCount]
    @Query("select o.branchId, count(o) from OrderEntity o "
        + "where o.status = com.savoris.constant.OrderStatus.COMPLETED and o.closedAt >= :since group by o.branchId")
    List<Object[]> countCompletedSinceGroupByBranch(@Param("since") LocalDateTime since);

    long countByBranchIdAndStatus(Integer branchId, OrderStatus status);

    @Query("select count(o) from OrderEntity o "
        + "where o.branchId = :branchId and o.status = com.savoris.constant.OrderStatus.COMPLETED and o.closedAt >= :since")
    long countCompletedSinceForBranch(@Param("branchId") Integer branchId, @Param("since") LocalDateTime since);

    // Locks the order row so pay / complete / cancel on the same order run one after another
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Integer id);
}
