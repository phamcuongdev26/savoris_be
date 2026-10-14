package com.savoris.constant;

public enum OrderStatus {
    // Web takeaway not paid yet: the kitchen does not see it until the cashier takes the money
    AWAITING_PAYMENT, OPEN, COMPLETED, CANCELLED
}
