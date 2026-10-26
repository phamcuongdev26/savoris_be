package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TakeawayEstimateRequest(
    @NotNull Integer branchId,
    @NotEmpty @Valid List<CreateOrderRequest.OrderItemRequest> items
) {}
