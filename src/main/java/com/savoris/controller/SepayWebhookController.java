package com.savoris.controller;

import com.savoris.payment.Sepay;
import com.savoris.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * SePay calls this for every transaction on the linked bank account. Money coming in with an order code
 * in its content pays that web takeaway order and sends it to the kitchen, with no cashier involved.
 */
@Slf4j
@RestController
@RequestMapping("/public/sepay")
@RequiredArgsConstructor
@Tag(name = "SePay", description = "Webhook báo tiền về tài khoản ngân hàng")
public class SepayWebhookController {

    private final Sepay sepay;
    private final PaymentService paymentService;

    // The fields of SePay's webhook body this endpoint reads; the others are ignored
    public record SepayTransaction(Long id, String gateway, String accountNumber, String subAccount, String content,
                                   String transferType, BigDecimal transferAmount, String referenceCode) {
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook SePay", description = "Header \"Authorization: Apikey <SEPAY_API_KEY>\". "
        + "Tiền vào có mã đơn trong nội dung và đủ số tiền thì đơn mang về được xác nhận đã thanh toán")
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody SepayTransaction tx) {
        if (!sepay.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!sepay.authorized(authorization)) {
            log.warn("SePay webhook refused: wrong or missing API key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String order = null;
        if ("in".equalsIgnoreCase(tx.transferType())) {
            String reference = ("SEPAY-" + tx.id() + (tx.referenceCode() != null ? " " + tx.referenceCode() : ""));
            order = paymentService.confirmBankTransfer(tx.id(), tx.content(), tx.transferAmount(),
                reference.length() > 100 ? reference.substring(0, 100) : reference, tx.accountNumber(), tx.subAccount());
        }
        // Always answer success once the call is genuine: SePay retries otherwise, and a retry would not match either
        return ResponseEntity.ok(order != null ? Map.of("success", true, "order", order) : Map.of("success", true));
    }

}
