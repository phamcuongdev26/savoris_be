package com.savoris.service.impl;

import com.savoris.dto.response.PointTransactionResponse;
import com.savoris.entity.CustomerEntity;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.PointTransactionEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.CustomerRepository;
import com.savoris.repository.PointTransactionRepository;
import com.savoris.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Loyalty points: earned when an order is completed (1 point per earn-per VND paid), spent on a later order
 * (1 point = point-value VND off). Every change is kept in point_transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyServiceImpl implements LoyaltyService {

    private static final String EARNED = "Earned on order ";
    private static final String SPENT = "Spent on order ";
    private static final String GIVEN_BACK = "Given back on order ";
    private static final String TAKEN_BACK = "Taken back, order refunded: ";

    private final CustomerRepository customerRepository;
    private final PointTransactionRepository pointTransactionRepository;

    @Value("${savoris.loyalty.earn-per:10000}")
    private BigDecimal earnPer;

    @Value("${savoris.loyalty.point-value:1000}")
    private BigDecimal pointValue;

    @Override
    public BigDecimal pointValue() {
        return pointValue;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void spend(Integer customerId, int points, OrderEntity order) {
        if (customerRepository.spendPoints(customerId, points) == 0) {
            int available = customerRepository.findById(customerId).map(CustomerEntity::getPoints).orElse(0);
            throw new AppException(ErrorCode.NOT_ENOUGH_POINTS, "Customer has " + available + " points, " + points + " requested");
        }
        record(customerId, order, -points, SPENT + order.getCode());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void giveBackSpent(OrderEntity order) {
        if (order.getPointsUsed() != null) {
            giveBack(order, order.getPointsUsed());
        }
    }

    // Points that no longer fit the order (cancelled order, or dishes removed so the bill got smaller)
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void giveBack(OrderEntity order, int points) {
        if (order.getCustomerId() == null || points <= 0) {
            return;
        }
        customerRepository.addPoints(order.getCustomerId(), points, BigDecimal.ZERO);
        record(order.getCustomerId(), order, points, GIVEN_BACK + order.getCode());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void earn(OrderEntity order) {
        if (order.getCustomerId() == null) {
            return;
        }
        int points = order.getTotalAmount().divide(earnPer, 0, RoundingMode.FLOOR).intValue();
        customerRepository.addPoints(order.getCustomerId(), points, order.getTotalAmount());
        if (points > 0) {
            record(order.getCustomerId(), order, points, EARNED + order.getCode());
        }
        log.info("Customer {} earned {} points on order {}", order.getCustomerId(), points, order.getCode());
    }

    // Refund of a completed order: the points it earned go back, as far as the customer has not spent them yet
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void takeBackEarned(OrderEntity order) {
        if (order.getCustomerId() == null) {
            return;
        }
        int earned = pointTransactionRepository.findByOrderId(order.getId()).stream()
            .filter(t -> t.getNote() != null && t.getNote().startsWith(EARNED))
            .mapToInt(PointTransactionEntity::getPoints)
            .sum();
        int available = customerRepository.findById(order.getCustomerId()).map(CustomerEntity::getPoints).orElse(0);
        int points = Math.min(earned, available);
        customerRepository.addPoints(order.getCustomerId(), -points, order.getTotalAmount().negate());
        if (points > 0) {
            record(order.getCustomerId(), order, -points, TAKEN_BACK + order.getCode());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointTransactionResponse> history(Integer customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Customer not found: " + customerId);
        }
        return pointTransactionRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
            .map(PointTransactionResponse::from)
            .toList();
    }

    private void record(Integer customerId, OrderEntity order, int points, String note) {
        PointTransactionEntity transaction = new PointTransactionEntity();
        transaction.setCustomerId(customerId);
        transaction.setOrderId(order.getId());
        transaction.setPoints(points);
        transaction.setNote(note);
        pointTransactionRepository.save(transaction);
    }
}
