package com.savoris.constant;

public enum KitchenLoadStatus {
    NORMAL,     // below 70% of kitchen capacity
    BUSY,       // 70% - 99%
    OVERLOADED  // at or above capacity: takeaway & delivery paused
}
