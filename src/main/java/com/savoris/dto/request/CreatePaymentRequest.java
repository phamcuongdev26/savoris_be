package com.savoris.dto.request;

import com.savoris.constant.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    @NotNull(message = "Order ID is required")
    private Integer orderId;

    // CASH (default) or BANK_TRANSFER; a transfer is only accepted on a web takeaway order whose guest chose it,
    // after the cashier saw the money arrive in the bank app (there is no bank integration)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    // Money the customer handed over (cash only); change is calculated by the server
    @DecimalMin(value = "0", message = "Cash received must not be negative")
    private BigDecimal cashReceived;
}
