package com.savoris.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TableRequest(
    @NotBlank(message = "Table name is required") @Size(max = 20) String name,
    @NotNull(message = "Capacity is required") @Positive @Max(100) Integer capacity
) {
}
