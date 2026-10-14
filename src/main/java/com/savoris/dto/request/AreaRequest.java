package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AreaRequest(
    @NotBlank(message = "Area name is required") @Size(max = 100) String name
) {
}
