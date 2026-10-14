package com.savoris.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChainDashboardResponse {
    private LocalDateTime generatedAt;
    private long openOrders;
    private long completedOrdersToday;
    private BigDecimal revenueToday;
    private long overloadedBranches;
    private List<BranchSummary> branches;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchSummary {
        private Integer branchId;
        private String code;
        private String name;
        private long openOrders;
        private long completedOrdersToday;
        private BigDecimal revenueToday;
        private BranchLoadResponse kitchen;
    }
}
