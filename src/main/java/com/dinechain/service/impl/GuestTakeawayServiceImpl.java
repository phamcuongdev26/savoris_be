package com.dinechain.service.impl;

import com.dinechain.constant.BranchStatus;
import com.dinechain.constant.OrderStatus;
import com.dinechain.constant.PaymentMethod;
import com.dinechain.constant.PaymentStatus;
import com.dinechain.dto.request.WebTakeawayRequest;
import com.dinechain.dto.response.BranchLoadResponse;
import com.dinechain.dto.response.GuestOrderResponse;
import com.dinechain.dto.response.GuestTableResponse;
import com.dinechain.dto.response.PublicBranchResponse;
import com.dinechain.dto.response.WebTakeawayResponse;
import com.dinechain.entity.BranchEntity;
import com.dinechain.entity.OrderEntity;
import com.dinechain.exception.AppException;
import com.dinechain.exception.ErrorCode;
import com.dinechain.repository.BranchRepository;
import com.dinechain.repository.MenuCategoryRepository;
import com.dinechain.repository.MenuItemRepository;
import com.dinechain.entity.OrderItemEntity;
import com.dinechain.repository.OrderItemRepository;
import com.dinechain.repository.OrderRepository;
import com.dinechain.repository.PaymentRepository;
import com.dinechain.repository.PushSubscriptionRepository;
import com.dinechain.entity.PushSubscriptionEntity;
import com.dinechain.notification.TakeawayNotifier;
import com.dinechain.notification.WebPushSender;
import com.dinechain.service.BranchMenuService;
import com.dinechain.service.GuestTakeawayService;
import com.dinechain.service.KitchenService;
import com.dinechain.service.OrderService;
import com.dinechain.payment.Sepay;
import com.dinechain.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Takeaway ordered from the website. The tracking token is the guest's only key to their order.
 */
@Service
@RequiredArgsConstructor
public class GuestTakeawayServiceImpl implements GuestTakeawayService {

    private final BranchRepository branchRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final PaymentRepository paymentRepository;
    private final BranchMenuService branchMenuService;
    private final KitchenService kitchenService;
    private final OrderService orderService;
    private final SettingService settingService;
    private final Sepay sepay;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final TakeawayNotifier takeawayNotifier;
    private final WebPushSender webPushSender;

    private static final List<String> PUSH_HOSTS = List.of(
        "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
        "notify.windows.com", "push.apple.com");

    @Override
    @Transactional(readOnly = true)
    public List<PublicBranchResponse> getBranches() {
        List<BranchEntity> branches = branchRepository.findAll().stream()
            .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
            .sorted(Comparator.comparing(BranchEntity::getCode))
            .toList();
        Map<Integer, BranchLoadResponse> loads = kitchenService.computeLoadOfBranches(branches);
        return branches.stream()
            .map(b -> new PublicBranchResponse(b.getId(), b.getCode(), b.getName(), b.getAddress(),
                loads.get(b.getId()).isAcceptingTakeaway(), loads.get(b.getId()).getEstimatedWaitMinutes(),
                settingService.transferAvailable(b.getId()),
                b.getCity(), b.getLatitude(), b.getLongitude()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GuestTableResponse getBranchMenu(Integer branchId) {
        BranchEntity branch = branchRepository.findById(branchId)
            .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        return new GuestTableResponse(branch.getCode(), branch.getName(), branch.getAddress(), null, null, null,
            settingService.getVatPercent(),
            menuCategoryRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(c -> new GuestTableResponse.Category(c.getId(), c.getName()))
                .toList(),
            branchMenuService.getPublicMenu(branch.getId()));
    }

    @Override
    public WebTakeawayResponse placeOrder(WebTakeawayRequest request) {
        return getOrder(orderService.placeWebTakeaway(request));
    }

    @Override
    @Transactional(readOnly = true)
    public WebTakeawayResponse getOrder(String token) {
        OrderEntity order = orderRepository.findByPublicToken(token)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown takeaway order"));
        BranchEntity branch = branchRepository.findById(order.getBranchId()).orElseThrow();
        boolean paid = paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PAID);
        // The QR is only worth showing while the guest still has to transfer
        boolean transferring = order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getPayMethod() == PaymentMethod.BANK_TRANSFER;
        SettingService.BankAccount account = transferring ? settingService.getBankAccount(order.getBranchId()) : null;
        String qr = !transferring ? null
            : account != null ? Sepay.qrUrl(account, order.getTotalAmount(), order.getCode())
            : settingService.getTransferQrUrl(order.getBranchId());
        List<OrderItemEntity> lines = orderItemRepository.findByOrderId(order.getId());
        Map<Integer, String> images = menuItemRepository.imagesOf(lines.stream().map(OrderItemEntity::getMenuItemId).toList());
        List<GuestOrderResponse.Item> items = lines.stream()
            .map(i -> new GuestOrderResponse.Item(i.getItemName(), i.getQuantity(), i.getAmount(), i.getStatus(), i.getNote(),
                images.get(i.getMenuItemId())))
            .toList();
        return new WebTakeawayResponse(order.getPublicToken(), order.getCode(), branch.getName(), branch.getAddress(),
            order.getGuestName(), order.getStatus(), order.getPayMethod(), paid, qr,
            order.getSubtotal(), order.getVatAmount(), order.getTotalAmount(), items,
            order.getTransferClaimedAt() != null, order.getCancelReason(),
            account, account != null && sepay.enabled());
    }

    @Override
    @Transactional
    public WebTakeawayResponse claimTransfer(String token) {
        OrderEntity order = orderRepository.findByPublicToken(token)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown takeaway order"));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getPayMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " is not waiting for a transfer");
        }
        if (order.getTransferClaimedAt() == null) {
            order.setTransferClaimedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
        return getOrder(token);
    }

    @Override
    @Transactional
    public void subscribe(String token, String endpoint, String p256dh, String auth) {
        OrderEntity order = orderRepository.findByPublicToken(token)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown takeaway order"));
        if (!isPushService(endpoint)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Not a browser push service");
        }
        PushSubscriptionEntity subscription = pushSubscriptionRepository.findByOrderIdAndEndpoint(order.getId(), endpoint)
            .orElseGet(PushSubscriptionEntity::new);
        subscription.setOrderId(order.getId());
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(p256dh);
        subscription.setAuth(auth);
        pushSubscriptionRepository.save(subscription);
        // Subscribed after the food was already done: tell them straight away
        takeawayNotifier.notifyIfReady(order);
    }

    @Override
    public String pushPublicKey() {
        return webPushSender.publicKey();
    }

    // The server only ever posts to the browsers' own push services, never to an address a caller picked
    private static boolean isPushService(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            return "https".equals(uri.getScheme()) && PUSH_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
