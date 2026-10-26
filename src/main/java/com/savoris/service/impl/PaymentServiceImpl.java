package com.savoris.service.impl;

import com.savoris.constant.OrderStatus;
import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import com.savoris.dto.request.CreatePaymentRequest;
import com.savoris.dto.response.PaymentResponse;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.PaymentEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.notification.TakeawayNotifier;
import com.savoris.payment.Sepay;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.repository.PaymentRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.SettingService;
import com.savoris.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash: the cashier enters the money the customer handed over and the server works out the change.
 * Bank transfer exists only for web takeaway: the guest transfers to the chain's QR, then either SePay reports
 * the money (confirmBankTransfer) or the cashier confirms it by hand.
 * Paying a web takeaway order releases it to the kitchen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final OrderItemRepository orderItemRepository;
    private final TakeawayNotifier takeawayNotifier;
    private final KitchenQueueEvents kitchenQueueEvents;
    private final SettingService settingService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public PaymentResponse processPayment(Integer staffId, CreatePaymentRequest request) {
        // Lock the order: two cashiers paying the same bill at once are handled one after the other
        OrderEntity order = orderRepository.findByIdForUpdate(request.getOrderId())
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + request.getOrderId()));
        branchAccessGuard.check(staffId, order.getBranchId());

        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            throw new AppException(ErrorCode.ALREADY_PAID, "Order " + order.getCode() + " has already been paid");
        }
        if (order.getStatus() != OrderStatus.OPEN && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " is " + order.getStatus());
        }

        PaymentMethod method = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH;
        BigDecimal total = order.getTotalAmount();
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(order.getId());
        payment.setMethod(method);
        payment.setAmount(total);
        payment.setStatus(PaymentStatus.PAID);
        payment.setReceivedBy(staffId);
        if (method == PaymentMethod.CASH) {
            BigDecimal cash = request.getCashReceived();
            if (cash == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Cash received is required");
            }
            if (cash.compareTo(total) < 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_CASH,
                    "Order total is " + total.toPlainString() + " but only " + cash.toPlainString() + " was received");
            }
            payment.setCashReceived(cash);
            payment.setChangeAmount(cash.subtract(total));
        } else if (method != PaymentMethod.BANK_TRANSFER || order.getPayMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        PaymentEntity saved = paymentRepository.save(payment);

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            sendToKitchen(order);
        }
        log.info("Order {} paid by {}: total {}", order.getCode(), method, total);
        return PaymentResponse.from(saved);
    }

    @Override
    @Transactional
    public String confirmBankTransfer(Long transactionId, String content, BigDecimal amount, String reference, String accountNumber, String subAccount) {
        for (String code : Sepay.orderCodesIn(content)) {
            Integer id = orderRepository.findByCode(code).map(OrderEntity::getId).orElse(null);
            if (id == null) {
                continue;
            }
            // Same lock as the cashier and the expiry job: whoever comes second sees the order already settled
            OrderEntity order = orderRepository.findByIdForUpdate(id).orElseThrow();
            if (order.getPayMethod() != PaymentMethod.BANK_TRANSFER || order.getPublicToken() == null) {
                continue;
            }
            if (!settingService.accountBelongsToBranch(order.getBranchId(), accountNumber, subAccount)) {
                log.warn("Transfer {} referenced order {} but arrived on another branch account", reference, code);
                continue;
            }
            // Claim each provider event once, across all app instances. The claim commits atomically with payment.
            // It is placed after matching the branch account, but before handling short/late/already-settled money.
            if (transactionId != null && !claimSePayTransaction(transactionId, id, reference)) {
                log.info("SePay transaction {} was already handled", transactionId);
                return null;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // Money for an order that already expired or was cancelled: the counter has to pay it back
                if (order.getRefundDueAt() == null) {
                    order.setRefundDueAt(LocalDateTime.now());
                    orderRepository.save(order);
                }
                log.warn("Transfer {} of {} for cancelled order {}: refund due", reference, amount, code);
                return null;
            }
            if (order.getStatus() != OrderStatus.AWAITING_PAYMENT || paymentRepository.findByOrderId(id).isPresent()) {
                // Already paid (the cashier confirmed this same money by hand, or SePay retried)
                log.info("Transfer {} of {} for order {} which is {}: already settled", reference, amount, code, order.getStatus());
                return null;
            }
            if (amount == null || amount.compareTo(order.getTotalAmount()) < 0) {
                log.warn("Transfer {} of {} for order {} is short of {}: left for the cashier", reference, amount, code, order.getTotalAmount());
                return null;
            }
            PaymentEntity payment = new PaymentEntity();
            payment.setOrderId(id);
            payment.setMethod(PaymentMethod.BANK_TRANSFER);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.PAID);
            payment.setReferenceCode(reference);
            paymentRepository.save(payment);
            sendToKitchen(order);
            log.info("Order {} paid by bank transfer {} ({}), confirmed by SePay", code, reference, amount);
            return code;
        }
        return null;
    }

    private void sendToKitchen(OrderEntity order) {
        order.setStatus(OrderStatus.OPEN);
        orderRepository.save(order);
        orderItemRepository.requeue(order.getId(), LocalDateTime.now());
        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Web takeaway {} paid: sent to the kitchen", order.getCode());
        // Only ready-made dishes: nothing to cook, the guest can come now
        takeawayNotifier.notifyIfReady(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Integer staffId, Integer paymentId) {
        PaymentEntity payment = findPayment(paymentId);
        checkAccess(staffId, payment.getOrderId());
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Integer staffId, Integer orderId) {
        checkAccess(staffId, orderId);
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Payment not found for order " + orderId));
        return PaymentResponse.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(Integer staffId, Integer paymentId) {
        Integer orderId = paymentRepository.findOrderIdById(paymentId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Payment not found: " + paymentId));
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + orderId));
        // Lock payment after order (the same lock ordering as all payment mutations) and read fresh state.
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Payment not found: " + paymentId));
        branchAccessGuard.check(staffId, order.getBranchId());

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.INVALID_STATE, "Payment " + paymentId + " is " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedBy(staffId);
        payment.setRefundedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Refunded {} for order {} by staff {}", payment.getAmount(), order.getCode(), staffId);
        return PaymentResponse.from(payment);
    }

    private PaymentEntity findPayment(Integer paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Payment not found: " + paymentId));
    }

    private boolean claimSePayTransaction(Long transactionId, Integer orderId, String reference) {
        return jdbcTemplate.update("""
            insert into sepay_transactions (transaction_id, order_id, reference_code, processed_at)
            values (?, ?, ?, now())
            on conflict (transaction_id) do nothing
            """, transactionId, orderId, reference) == 1;
    }

    private void checkAccess(Integer staffId, Integer orderId) {
        OrderEntity order = orderRepository.findById(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + orderId));
        branchAccessGuard.check(staffId, order.getBranchId());
    }
}
