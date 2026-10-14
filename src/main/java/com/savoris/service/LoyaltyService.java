package com.savoris.service;

import com.savoris.dto.response.PointTransactionResponse;
import com.savoris.entity.OrderEntity;

import java.math.BigDecimal;
import java.util.List;

public interface LoyaltyService {
    /** Money one point is worth when spent. */
    BigDecimal pointValue();
    void spend(Integer customerId, int points, OrderEntity order);
    void giveBackSpent(OrderEntity order);
    void giveBack(OrderEntity order, int points);
    void earn(OrderEntity order);
    void takeBackEarned(OrderEntity order);
    List<PointTransactionResponse> history(Integer customerId);
}
