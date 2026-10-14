package com.dinechain.service;

import com.dinechain.dto.request.CreatePaymentRequest;
import com.dinechain.dto.response.PaymentResponse;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentResponse processPayment(Integer staffId, CreatePaymentRequest request);
    PaymentResponse getPayment(Integer staffId, Integer paymentId);
    PaymentResponse getPaymentByOrder(Integer staffId, Integer orderId);
    PaymentResponse refundPayment(Integer staffId, Integer paymentId);
    // A transfer the bank reported through SePay: pays the web takeaway order whose code is in the content.
    // Returns the paid order's code, or null when no unpaid transfer order matched
    String confirmBankTransfer(String content, BigDecimal amount, String reference, String accountNumber, String subAccount);
}
