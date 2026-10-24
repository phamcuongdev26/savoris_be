package com.savoris.service.impl;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.KitchenLoadStatus;
import com.savoris.constant.OrderStatus;
import com.savoris.constant.ReservationStatus;
import com.savoris.dto.response.BranchDashboardResponse;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.ChainDashboardResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.repository.PaymentRepository;
import com.savoris.repository.ReservationRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.DashboardService;
import com.savoris.service.KitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final KitchenService kitchenService;
    private final BranchAccessGuard branchAccessGuard;

    // Fixed number of queries no matter how many branches the chain has
    @Override
    @Transactional(readOnly = true)
    public ChainDashboardResponse getChainDashboard() {
        List<BranchEntity> branches = branchRepository.findByStatus(BranchStatus.ACTIVE);

        Map<Integer, Long> openOrders = new HashMap<>();
        for (Object[] row : orderRepository.countByStatusGroupByBranch(OrderStatus.OPEN)) {
            openOrders.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Map<Integer, Long> completedToday = new HashMap<>();
        for (Object[] row : orderRepository.countCompletedSinceGroupByBranch(startOfDay)) {
            completedToday.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        // Revenue = cash actually taken today (refunds excluded)
        Map<Integer, BigDecimal> revenueToday = new HashMap<>();
        for (Object[] row : paymentRepository.sumPaidSinceGroupByBranch(startOfDay)) {
            revenueToday.put((Integer) row[0], (BigDecimal) row[1]);
        }
        Map<Integer, BranchLoadResponse> loads = kitchenService.computeLoadOfBranches(branches);

        List<ChainDashboardResponse.BranchSummary> summaries = branches.stream()
            .map(branch -> new ChainDashboardResponse.BranchSummary(
                branch.getId(),
                branch.getCode(),
                branch.getName(),
                openOrders.getOrDefault(branch.getId(), 0L),
                completedToday.getOrDefault(branch.getId(), 0L),
                revenueToday.getOrDefault(branch.getId(), BigDecimal.ZERO),
                loads.get(branch.getId())))
            .toList();

        return new ChainDashboardResponse(
            LocalDateTime.now(),
            summaries.stream().mapToLong(ChainDashboardResponse.BranchSummary::getOpenOrders).sum(),
            summaries.stream().mapToLong(ChainDashboardResponse.BranchSummary::getCompletedOrdersToday).sum(),
            summaries.stream().map(ChainDashboardResponse.BranchSummary::getRevenueToday).reduce(BigDecimal.ZERO, BigDecimal::add),
            summaries.stream().filter(s -> s.getKitchen().getStatus() == KitchenLoadStatus.OVERLOADED).count(),
            summaries
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDashboardResponse getBranchDashboard(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND, "Branch not found: " + branchId));

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        long openOrders = orderRepository.countByBranchIdAndStatus(branchId, OrderStatus.OPEN);
        long completedToday = orderRepository.countCompletedSinceForBranch(branchId, startOfDay);
        BigDecimal revenueToday = paymentRepository.sumPaidSinceForBranch(branchId, startOfDay);
        long reservationsToday = reservationRepository
            .findByBranchIdAndReservedAtBetweenAndStatusNot(branchId, startOfDay, endOfDay, ReservationStatus.CANCELLED)
            .size();
        BranchLoadResponse kitchen = kitchenService.computeLoad(branch);

        return new BranchDashboardResponse(
            LocalDateTime.now(),
            branch.getId(),
            branch.getCode(),
            branch.getName(),
            openOrders,
            completedToday,
            revenueToday,
            reservationsToday,
            kitchen
        );
    }
}
