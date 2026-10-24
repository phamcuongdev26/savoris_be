package com.savoris.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardResponse {
    private LocalDateTime generatedAt;
    private Integer branchId;
    private String code;
    private String name;
    private long openOrders;
    private long completedOrdersToday;
    private BigDecimal revenueToday;
    private long reservationsToday;
    private BranchLoadResponse kitchen;
}
