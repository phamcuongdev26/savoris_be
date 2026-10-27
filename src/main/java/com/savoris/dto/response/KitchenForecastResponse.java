package com.savoris.dto.response;

import java.util.List;

public record KitchenForecastResponse(Integer branchId, String branchName, int kitchenCapacity,
    int activeChefAccounts, long pendingPortions, long cookingPortions, int currentLoadPercent,
    List<HistoricalHour> history, List<ForecastHour> forecast, String alert, List<String> actions,
    String suggestedPauseAt, String dataNote, boolean sampleData) {
    public record HistoricalHour(String hour, double averageOrders, double averagePortions, double averageLoadPercent) {}
    public record ForecastHour(String hour, int predictedLoad, String risk) {}
}
