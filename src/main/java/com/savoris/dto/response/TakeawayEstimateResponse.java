package com.savoris.dto.response;

import java.time.LocalDateTime;

public record TakeawayEstimateResponse(
    int estimatedMinutes,
    LocalDateTime pickupAt,
    String closingTime,
    boolean canAccept,
    String message
) {}
