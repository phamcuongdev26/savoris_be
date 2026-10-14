package com.savoris.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderItemRequest {
    // e.g. "khách đổi ý", "món ra nhầm"
    @Size(max = 255)
    private String reason;
}
