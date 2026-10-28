package com.savoris.dto.request;

import java.util.List;

public record KitchenForecastSampleRequest(
    Integer kitchenCapacity,
    Long pendingPortions,
    Long cookingPortions,
    Integer currentLoadPercent,
    Integer activeChefAccounts,
    List<HistoryHour> history
) {
    public record HistoryHour(Integer hour, Double averageOrders, Double averagePortions) {}
}
