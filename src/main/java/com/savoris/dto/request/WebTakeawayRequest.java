package com.savoris.dto.request;

import com.savoris.constant.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A takeaway order a guest places on the website. payMethod: CASH (pay at the counter) or BANK_TRANSFER.
 */
public record WebTakeawayRequest(
    @NotNull(message = "Branch is required") Integer branchId,
    @NotBlank(message = "Name is required") @Size(max = 100) String guestName,
    @NotBlank(message = "Phone is required") @Pattern(regexp = "^0\\d{9,10}$", message = "Phone must be 10-11 digits starting with 0")
    String guestPhone,
    @NotNull(message = "Payment method is required") PaymentMethod payMethod,
    @NotNull(message = "Pickup time is required") LocalDateTime pickupAt,
    @Size(max = 500) String notes,
    @NotEmpty(message = "Order must contain at least one item") @Valid List<CreateOrderRequest.OrderItemRequest> items
) {
}
