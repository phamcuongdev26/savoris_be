package com.savoris.service.impl;

import com.savoris.constant.KitchenLoadStatus;
import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.OrderStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenTicketResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.OrderItemEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.notification.TakeawayNotifier;
import com.savoris.service.KitchenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenServiceImpl implements KitchenService {

    private static final List<OrderItemStatus> QUEUE_STATUSES =
        List.of(OrderItemStatus.PENDING, OrderItemStatus.COOKING, OrderItemStatus.VOID_REQUESTED, OrderItemStatus.READY);
    private static final List<OrderItemStatus> ACTIVE_STATUSES =
        List.of(OrderItemStatus.PENDING, OrderItemStatus.COOKING);
    private static final int THROUGHPUT_WINDOW_MINUTES = 30;
    // Too few finished dishes say nothing about kitchen speed; better no estimate than a wild one
    private static final int MIN_SAMPLE_PORTIONS = 5;
    private static final int MIN_SAMPLE_MINUTES = 5;
    private static final int BUSY_PERCENT = 70;

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final TakeawayNotifier takeawayNotifier;

    @Value("${savoris.kitchen.default-capacity:40}")
    private int defaultCapacity;

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicketResponse> getQueue(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);

        List<OrderItemEntity> items = orderItemRepository.findKitchenQueue(branchId, QUEUE_STATUSES);
        Map<Integer, OrderEntity> orders = orderRepository
            .findAllById(items.stream().map(OrderItemEntity::getOrderId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        return items.stream()
            .map(item -> KitchenTicketResponse.from(item, orders.get(item.getOrderId()), now))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenTicketResponse> getVoidQueue(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        List<OrderItemEntity> items = orderItemRepository.findKitchenQueue(
            branchId, List.of(OrderItemStatus.VOID_REQUESTED));
        Map<Integer, OrderEntity> orders = orderRepository
            .findAllById(items.stream().map(OrderItemEntity::getOrderId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        return items.stream()
            .map(item -> KitchenTicketResponse.from(item, orders.get(item.getOrderId()), now))
            .toList();
    }

    @Override
    @Transactional
    public KitchenTicketResponse startCooking(Integer staffId, Integer itemId) {
        return move(staffId, itemId, OrderItemStatus.PENDING, OrderItemStatus.COOKING);
    }

    @Override
    @Transactional
    public KitchenTicketResponse markReady(Integer staffId, Integer itemId) {
        return move(staffId, itemId, OrderItemStatus.COOKING, OrderItemStatus.READY);
    }

    @Override
    @Transactional
    public KitchenTicketResponse markServed(Integer staffId, Integer itemId) {
        return move(staffId, itemId, OrderItemStatus.READY, OrderItemStatus.SERVED);
    }

    private KitchenTicketResponse move(Integer staffId, Integer itemId, OrderItemStatus from, OrderItemStatus to) {
        OrderItemEntity item = orderItemRepository.findById(itemId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order item not found: " + itemId));
        OrderEntity order = orderRepository.findById(item.getOrderId())
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + item.getOrderId()));
        branchAccessGuard.check(staffId, order.getBranchId());
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " has not been paid yet");
        }

        // Atomic check-and-update: when two cooks tap the same dish, exactly one wins
        LocalDateTime now = LocalDateTime.now();
        if (orderItemRepository.transition(itemId, from, to, now) == 0) {
            throw new AppException(ErrorCode.INVALID_STATE,
                "Item " + itemId + " is not " + from + " anymore");
        }

        OrderItemEntity updated = orderItemRepository.findById(itemId).orElseThrow();
        switch (to) {
            case COOKING -> updated.setCookedBy(staffId);
            case READY -> updated.setReadyAt(now);
            case SERVED -> updated.setServedBy(staffId);
            default -> { }
        }
        orderItemRepository.save(updated);
        if (to == OrderItemStatus.READY) {
            takeawayNotifier.notifyIfReady(order);
        }

        log.info("Order item {} of order {}: {} -> {} by staff {}", itemId, order.getCode(), from, to, staffId);
        return KitchenTicketResponse.from(updated, order, now);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchLoadResponse getLoad(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        return computeLoad(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchLoadResponse computeLoad(BranchEntity branch) {
        long pending = orderItemRepository.sumQuantityByBranchAndStatus(branch.getId(), List.of(OrderItemStatus.PENDING));
        long cooking = orderItemRepository.sumQuantityByBranchAndStatus(branch.getId(), List.of(OrderItemStatus.COOKING));
        Object[] ready = orderItemRepository.readyStatsSince(branch.getId(), throughputWindowStart()).get(0);
        return buildLoad(branch, pending, cooking, ((Number) ready[0]).longValue(), (LocalDateTime) ready[1]);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, BranchLoadResponse> computeLoadOfBranches(List<BranchEntity> branches) {
        Map<Integer, Long> pending = new HashMap<>();
        Map<Integer, Long> cooking = new HashMap<>();
        for (Object[] row : orderItemRepository.sumQuantityByStatusGroupByBranch(ACTIVE_STATUSES)) {
            Map<Integer, Long> target = row[1] == OrderItemStatus.PENDING ? pending : cooking;
            target.put((Integer) row[0], ((Number) row[2]).longValue());
        }
        Map<Integer, Object[]> readyRecently = new HashMap<>();
        for (Object[] row : orderItemRepository.readyStatsSinceGroupByBranch(throughputWindowStart())) {
            readyRecently.put((Integer) row[0], row);
        }

        Map<Integer, BranchLoadResponse> result = new HashMap<>();
        for (BranchEntity branch : branches) {
            Integer id = branch.getId();
            Object[] ready = readyRecently.get(id);
            result.put(id, buildLoad(branch, pending.getOrDefault(id, 0L), cooking.getOrDefault(id, 0L),
                ready != null ? ((Number) ready[1]).longValue() : 0L, ready != null ? (LocalDateTime) ready[2] : null));
        }
        return result;
    }

    private LocalDateTime throughputWindowStart() {
        return LocalDateTime.now().minusMinutes(THROUGHPUT_WINDOW_MINUTES);
    }

    private BranchLoadResponse buildLoad(BranchEntity branch, long pending, long cooking,
                                         long readyRecently, LocalDateTime firstReadyAt) {
        int capacity = branch.getKitchenCapacity() != null ? branch.getKitchenCapacity() : defaultCapacity;
        long active = pending + cooking;
        int loadPercent = (int) (active * 100 / capacity);

        // Wait = portions ahead in the queue / kitchen speed, where speed = portions finished per minute
        // over the time the kitchen has actually been finishing dishes (5 to 30 minutes)
        Integer waitMinutes;
        if (active == 0) {
            waitMinutes = 0;
        } else if (readyRecently < MIN_SAMPLE_PORTIONS) {
            waitMinutes = null;
        } else {
            long workedMinutes = Duration.between(firstReadyAt, LocalDateTime.now()).toMinutes();
            long sampleMinutes = Math.max(MIN_SAMPLE_MINUTES, Math.min(THROUGHPUT_WINDOW_MINUTES, workedMinutes));
            waitMinutes = (int) Math.ceil(active * (double) sampleMinutes / readyRecently);
        }

        KitchenLoadStatus status = loadPercent >= 100 ? KitchenLoadStatus.OVERLOADED
            : loadPercent >= BUSY_PERCENT ? KitchenLoadStatus.BUSY
            : KitchenLoadStatus.NORMAL;

        return new BranchLoadResponse(branch.getId(), pending, cooking, capacity, loadPercent, readyRecently,
            waitMinutes, status, status != KitchenLoadStatus.OVERLOADED);
    }
}
