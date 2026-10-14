package com.savoris.dto.response;

import com.savoris.constant.KitchenLoadStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchLoadResponse {
    private Integer branchId;
    private long pendingPortions;
    private long cookingPortions;
    private int kitchenCapacity;
    private int loadPercent;
    private long portionsReadyLast30Min;
    // null when there is a queue but the kitchen finished fewer than 5 portions in the last 30 minutes (not enough data)
    private Integer estimatedWaitMinutes;
    private KitchenLoadStatus status;
    private boolean acceptingTakeaway;
}
