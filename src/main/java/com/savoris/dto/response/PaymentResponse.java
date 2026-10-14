package com.savoris.dto.response;

import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import com.savoris.entity.PaymentEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Integer id;
    private Integer orderId;
    private BigDecimal amount;
    private BigDecimal cashReceived;
    private BigDecimal changeAmount;
    private PaymentMethod method;
    private PaymentStatus status;
    private Integer receivedBy;
    private LocalDateTime paidAt;
    private Integer refundedBy;
    private LocalDateTime refundedAt;

    public static PaymentResponse from(PaymentEntity entity) {
        return new PaymentResponse(entity.getId(), entity.getOrderId(), entity.getAmount(), entity.getCashReceived(),
            entity.getChangeAmount(), entity.getMethod(), entity.getStatus(), entity.getReceivedBy(), entity.getPaidAt(),
            entity.getRefundedBy(), entity.getRefundedAt());
    }
}
