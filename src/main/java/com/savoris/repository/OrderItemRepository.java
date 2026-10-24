package com.savoris.repository;

import com.savoris.constant.OrderItemStatus;
import com.savoris.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Integer> {
    List<OrderItemEntity> findByOrderId(Integer orderId);

    List<OrderItemEntity> findByOrderIdIn(Collection<Integer> orderIds);

    @Query("select i from OrderItemEntity i join OrderEntity o on o.id = i.orderId "
        + "where o.branchId = :branchId and i.status in :statuses"
        + " and i.needsKitchen = true order by i.orderedAt, i.id")
    List<OrderItemEntity> findKitchenQueue(@Param("branchId") Integer branchId,
                                           @Param("statuses") Collection<OrderItemStatus> statuses);

    // Compare-and-set: only one caller can move an item out of a given status
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemEntity i set i.status = :to, i.updatedAt = :now where i.id = :id and i.status = :from")
    int transition(@Param("id") Integer id, @Param("from") OrderItemStatus from,
                   @Param("to") OrderItemStatus to, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemEntity i set i.status = com.savoris.constant.OrderItemStatus.CANCELLED, i.updatedAt = :now "
        + "where i.orderId = :orderId and i.status in :statuses")
    int cancelByOrderId(@Param("orderId") Integer orderId, @Param("statuses") Collection<OrderItemStatus> statuses,
                        @Param("now") LocalDateTime now);

    // Paid web takeaway joins the kitchen queue at the moment it was paid, not when it was ordered
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderItemEntity i set i.orderedAt = :now where i.orderId = :orderId")
    int requeue(@Param("orderId") Integer orderId, @Param("now") LocalDateTime now);

    long countByOrderIdAndStatusIn(Integer orderId, Collection<OrderItemStatus> statuses);

    // Portions waiting or cooking in one branch
    @Query("select coalesce(sum(i.quantity), 0) from OrderItemEntity i join OrderEntity o on o.id = i.orderId "
        + "where o.branchId = :branchId and i.status in :statuses and o.status <> com.savoris.constant.OrderStatus.AWAITING_PAYMENT"
        + " and i.needsKitchen = true")
    long sumQuantityByBranchAndStatus(@Param("branchId") Integer branchId,
                                      @Param("statuses") Collection<OrderItemStatus> statuses);

    // Portions the kitchen of one branch finished since a point in time, and when the first of them was finished.
    // One row of [quantity, firstReadyAt]
    @Query("select coalesce(sum(i.quantity), 0), min(i.readyAt) from OrderItemEntity i join OrderEntity o on o.id = i.orderId "
        + "where o.branchId = :branchId and i.readyAt >= :since and i.needsKitchen = true")
    List<Object[]> readyStatsSince(@Param("branchId") Integer branchId, @Param("since") LocalDateTime since);

    // Same two metrics for every branch at once. Rows of [branchId, status, quantity]
    @Query("select o.branchId, i.status, sum(i.quantity) from OrderItemEntity i join OrderEntity o on o.id = i.orderId "
        + "where i.status in :statuses and o.status <> com.savoris.constant.OrderStatus.AWAITING_PAYMENT"
        + " and i.needsKitchen = true group by o.branchId, i.status")
    List<Object[]> sumQuantityByStatusGroupByBranch(@Param("statuses") Collection<OrderItemStatus> statuses);

    // Rows of [branchId, quantity, firstReadyAt]
    @Query("select o.branchId, sum(i.quantity), min(i.readyAt) from OrderItemEntity i join OrderEntity o on o.id = i.orderId "
        + "where i.readyAt >= :since and i.needsKitchen = true group by o.branchId")
    List<Object[]> readyStatsSinceGroupByBranch(@Param("since") LocalDateTime since);
}
