package com.savoris.service.impl;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.OrderStatus;
import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import com.savoris.dto.request.WebTakeawayRequest;
import com.savoris.dto.request.CreateOrderRequest;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.GuestOrderResponse;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicBranchResponse;
import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.dto.response.WebTakeawayResponse;
import com.savoris.dto.response.TakeawayEstimateResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.MenuCategoryRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.entity.OrderItemEntity;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.repository.PaymentRepository;
import com.savoris.repository.PushSubscriptionRepository;
import com.savoris.entity.PushSubscriptionEntity;
import com.savoris.notification.TakeawayNotifier;
import com.savoris.notification.WebPushSender;
import com.savoris.service.BranchMenuService;
import com.savoris.service.GuestTakeawayService;
import com.savoris.service.KitchenService;
import com.savoris.service.OrderService;
import com.savoris.payment.Sepay;
import com.savoris.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final JdbcTemplate jdbc;

    private static final List<String> PUSH_HOSTS = List.of(
        "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
        "notify.windows.com", "push.apple.com");
    private static final int PREP_HISTORY_DAYS = 90;
    private static final int MIN_PREP_SAMPLES = 5;
    private static final int DEFAULT_DISH_PREP_MINUTES = 15;
    private static final int PACKING_BUFFER_MINUTES = 5;

    @Override
    @Transactional(readOnly = true)
    public List<PublicBranchResponse> getBranches() {
        List<BranchEntity> branches = branchRepository.findAll().stream()
            .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
            .sorted(Comparator.comparing(BranchEntity::getCode))
            .toList();
        Map<Integer, BranchLoadResponse> loads = kitchenService.computeLoadOfBranches(branches);
        return branches.stream()
            .map(b -> {
                boolean openNow = isOpenNow(b.getId());
                return new PublicBranchResponse(b.getId(), b.getCode(), b.getName(), b.getSlug(), b.getAddress(),
                    openNow && loads.get(b.getId()).isAcceptingTakeaway(), loads.get(b.getId()).getEstimatedWaitMinutes(),
                    settingService.transferAvailable(b.getId()), b.getCity(), b.getLatitude(), b.getLongitude(), openNow,
                    b.getPhone(), b.getEmail(), jdbc.query(
                        "select weekday, opens_at, closes_at, closed from opening_hour where branch_id=? order by weekday",
                        (rs, row) -> new PublicBranchResponse.Hours(dayName(rs.getInt("weekday")), formatHours(
                            rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class), rs.getBoolean("closed"))), b.getId()));
            })
            .toList();
    }

    private String dayName(int weekday) {
        return switch (weekday) {
            case 1 -> "Thứ Hai"; case 2 -> "Thứ Ba"; case 3 -> "Thứ Tư"; case 4 -> "Thứ Năm";
            case 5 -> "Thứ Sáu"; case 6 -> "Thứ Bảy"; default -> "Chủ Nhật";
        };
    }

    private String formatHours(LocalTime opensAt, LocalTime closesAt, boolean closed) {
        if (closed) return "Nghỉ";
        if (opensAt == null || closesAt == null) return "Đang cập nhật";
        return opensAt.toString().substring(0, 5) + " – " + closesAt.toString().substring(0, 5);
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
            branchMenuService.getPublicMenu(branch.getId()).stream()
                .filter(BranchMenuItemResponse::isTakeawayAvailable)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TakeawayEstimateResponse estimatePickup(Integer branchId, List<CreateOrderRequest.OrderItemRequest> items) {
        return calculatePickupEstimate(branchId, items);
    }

    private TakeawayEstimateResponse calculatePickupEstimate(Integer branchId,
            List<CreateOrderRequest.OrderItemRequest> items) {
        BranchEntity branch = branchRepository.findById(branchId)
            .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!isOpenNow(branchId)) {
            return new TakeawayEstimateResponse(0, null, null, false, "Chi nhánh đã đóng cửa, hiện không nhận đơn mang về.");
        }

        BranchLoadResponse load = kitchenService.computeLoad(branch);
        if (!load.isAcceptingTakeaway()) {
            return new TakeawayEstimateResponse(0, null, null, false, "Bếp đang tạm ngưng nhận đơn mang về.");
        }

        java.util.Set<Integer> branchTakeawayMenuIds = branchMenuService.getPublicMenu(branchId).stream()
            .filter(BranchMenuItemResponse::isAvailable)
            .filter(BranchMenuItemResponse::isTakeawayAvailable)
            .map(BranchMenuItemResponse::getMenuItemId)
            .collect(Collectors.toSet());
        if (items.stream().anyMatch(item -> !branchTakeawayMenuIds.contains(item.getMenuItemId()))) {
            throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, "Món không bán hoặc hiện không có tại chi nhánh này.");
        }

        Map<Integer, MenuItemEntity> menuItems = menuItemRepository.findAllById(
                items.stream().map(CreateOrderRequest.OrderItemRequest::getMenuItemId).distinct().toList())
            .stream().collect(Collectors.toMap(MenuItemEntity::getId, Function.identity()));
        if (menuItems.size() != items.stream().map(CreateOrderRequest.OrderItemRequest::getMenuItemId).distinct().count()
                || items.stream().anyMatch(item -> !Boolean.TRUE.equals(menuItems.get(item.getMenuItemId()).getTakeawayAvailable()))) {
            throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, "Món này không có sẵn để mang về.");
        }

        LocalDateTime historySince = now.minusDays(PREP_HISTORY_DAYS);
        PrepStats branchStats = prepStats(branchId, null, historySince);
        int slowestDishMinutes = 0;
        for (CreateOrderRequest.OrderItemRequest line : items) {
            MenuItemEntity dish = menuItems.get(line.getMenuItemId());
            if (Boolean.FALSE.equals(dish.getNeedsKitchen())) continue;
            PrepStats dishStats = prepStats(branchId, dish.getId(), historySince);
            int perPortionMinutes = dishStats.samples() >= MIN_PREP_SAMPLES
                ? dishStats.p75Minutes()
                : branchStats.samples() >= MIN_PREP_SAMPLES ? branchStats.p75Minutes() : DEFAULT_DISH_PREP_MINUTES;
            int lineMinutes = (int) Math.ceil(perPortionMinutes * Math.sqrt(line.getQuantity()));
            slowestDishMinutes = Math.max(slowestDishMinutes, lineMinutes);
        }

        long activePortions = load.getPendingPortions() + load.getCookingPortions();
        int queueMinutes = load.getEstimatedWaitMinutes() != null
            ? load.getEstimatedWaitMinutes()
            : activePortions == 0 ? 0 : Math.min(90, Math.max(10, (int) Math.ceil(activePortions * 1.5)));
        int rawMinutes = Math.min(120, Math.max(5, queueMinutes + slowestDishMinutes + PACKING_BUFFER_MINUTES));
        int roundedMinutes = ((rawMinutes + 4) / 5) * 5;
        LocalDateTime pickupAt = roundUpToQuarterHour(now.plusMinutes(roundedMinutes));
        int estimatedMinutes = Math.max(5, (int) Duration.between(now, pickupAt).toMinutes());

        int weekday = pickupAt.getDayOfWeek().getValue();
        List<OpeningHours> hoursForDay = jdbc.query(
            "select opens_at, closes_at, closed from opening_hour where branch_id=? and weekday=?",
            (rs, rowNum) -> new OpeningHours(rs.getObject("opens_at", LocalTime.class),
                rs.getObject("closes_at", LocalTime.class), rs.getBoolean("closed")), branchId, weekday);
        if (hoursForDay.isEmpty() || hoursForDay.get(0).closed()
                || hoursForDay.get(0).opensAt() == null || hoursForDay.get(0).closesAt() == null) {
            return new TakeawayEstimateResponse(estimatedMinutes, pickupAt, null, false,
                "Chi nhánh không hoạt động vào thời điểm dự kiến nhận món.");
        }
        if (pickupAt.toLocalTime().isBefore(hoursForDay.get(0).opensAt())) {
            String openingTime = hoursForDay.get(0).opensAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            return new TakeawayEstimateResponse(estimatedMinutes, pickupAt,
                hoursForDay.get(0).closesAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), false,
                "Thời gian dự kiến trước giờ mở cửa " + openingTime + ". Chi nhánh không thể nhận đơn này.");
        }
        String closingTime = hoursForDay.get(0).closesAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (!pickupAt.toLocalTime().isBefore(hoursForDay.get(0).closesAt())) {
            return new TakeawayEstimateResponse(estimatedMinutes, pickupAt, closingTime, false,
                "Thời gian dự kiến vượt giờ đóng cửa. Vui lòng nhận món trước " + closingTime + ".");
        }
        return new TakeawayEstimateResponse(estimatedMinutes, pickupAt, closingTime, true,
            "Vui lòng nhận món trước " + closingTime + " (giờ đóng cửa của chi nhánh).");
    }

    private PrepStats prepStats(Integer branchId, Integer menuItemId, LocalDateTime since) {
        String menuFilter = menuItemId == null ? "" : "and oi.menu_item_id = ? ";
        String sql = "select count(*) as samples, percentile_cont(0.75) within group " +
            "(order by extract(epoch from (oi.ready_at - oi.cooking_started_at)) / 60.0 / sqrt(oi.quantity)) as p75_minutes " +
            "from order_items oi join orders o on o.id = oi.order_id " +
            "where o.branch_id = ? and oi.needs_kitchen = true and oi.cooking_started_at is not null and oi.ready_at is not null " +
            "and oi.ready_at >= oi.cooking_started_at and oi.cooking_started_at >= ? and oi.status in ('READY','SERVED') " + menuFilter;
        Map<String, Object> stats = menuItemId == null
            ? jdbc.queryForMap(sql, branchId, since)
            : jdbc.queryForMap(sql, branchId, since, menuItemId);
        int samples = ((Number) stats.get("samples")).intValue();
        Number rawMinutes = (Number) stats.get("p75_minutes");
        int p75Minutes = rawMinutes == null ? DEFAULT_DISH_PREP_MINUTES
            : Math.max(5, Math.min(90, (int) Math.ceil(rawMinutes.doubleValue())));
        return new PrepStats(samples, p75Minutes);
    }

    private LocalDateTime roundUpToQuarterHour(LocalDateTime value) {
        LocalDateTime minute = value.withSecond(0).withNano(0);
        int remainder = minute.getMinute() % 15;
        return remainder == 0 ? minute : minute.plusMinutes(15 - remainder);
    }

    private record PrepStats(int samples, int p75Minutes) {}

    @Override
    @Transactional
    public WebTakeawayResponse placeOrder(WebTakeawayRequest request) {
        Map<Integer, Boolean> takeawayFlags = menuItemRepository.findAllById(
                request.items().stream().map(com.savoris.dto.request.CreateOrderRequest.OrderItemRequest::getMenuItemId).toList())
            .stream().collect(java.util.stream.Collectors.toMap(MenuItemEntity::getId,
                item -> !Boolean.FALSE.equals(item.getTakeawayAvailable())));
        boolean containsTakeawayDisabled = request.items().stream()
            .anyMatch(item -> !Boolean.TRUE.equals(takeawayFlags.get(item.getMenuItemId())));
        if (containsTakeawayDisabled) {
            throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, "Món này không bán mang về");
        }
        if (!isOpenNow(request.branchId())) {
            throw new AppException(ErrorCode.INVALID_STATE, "Chi nhánh đã đóng cửa theo giờ hoạt động");
        }
        TakeawayEstimateResponse estimate = calculatePickupEstimate(request.branchId(), request.items());
        if (!estimate.canAccept() || estimate.pickupAt() == null) {
            throw new AppException(ErrorCode.INVALID_STATE, estimate.message());
        }
        validatePickupTime(request.branchId(), estimate.pickupAt());
        WebTakeawayRequest estimatedRequest = new WebTakeawayRequest(request.branchId(), request.guestName(),
            request.guestPhone(), request.payMethod(), estimate.pickupAt(), request.notes(), request.items());
        return getOrder(orderService.placeWebTakeaway(estimatedRequest));
    }

    private void validatePickupTime(Integer branchId, LocalDateTime pickupAt) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (pickupAt == null || !pickupAt.isAfter(now)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Giờ lấy món phải ở thời điểm trong tương lai");
        }
        int weekday = pickupAt.getDayOfWeek().getValue();
        List<OpeningHours> hoursForDay = jdbc.query(
            "select opens_at, closes_at, closed from opening_hour where branch_id=? and weekday=?",
            (rs, rowNum) -> new OpeningHours(rs.getObject("opens_at", LocalTime.class),
                rs.getObject("closes_at", LocalTime.class), rs.getBoolean("closed")),
            branchId, weekday);
        if (hoursForDay.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_STATE, "Chi nhánh chưa cài giờ mở cửa cho ngày lấy món");
        }
        OpeningHours hours = hoursForDay.get(0);
        LocalTime time = pickupAt.toLocalTime();
        if (hours.closed() || hours.opensAt() == null || hours.closesAt() == null
                || time.isBefore(hours.opensAt()) || !time.isBefore(hours.closesAt())) {
            throw new AppException(ErrorCode.INVALID_STATE, "Giờ lấy món nằm ngoài giờ hoạt động của chi nhánh");
        }
    }

    private boolean isOpenNow(Integer branchId) {
        var now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int weekday = now.getDayOfWeek().getValue();
        List<OpeningHours> today = jdbc.query(
            "select opens_at, closes_at, closed from opening_hour where branch_id=? and weekday=?",
            (rs, rowNum) -> new OpeningHours(rs.getObject("opens_at", LocalTime.class),
                rs.getObject("closes_at", LocalTime.class), rs.getBoolean("closed")),
            branchId, weekday);
        if (today.isEmpty()) return false;
        OpeningHours hours = today.get(0);
        LocalTime time = now.toLocalTime();
        return !hours.closed() && hours.opensAt() != null && hours.closesAt() != null
            && !time.isBefore(hours.opensAt()) && time.isBefore(hours.closesAt());
    }

    private record OpeningHours(LocalTime opensAt, LocalTime closesAt, boolean closed) {}

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
        String customQr = transferring ? settingService.getTransferQrUrl(order.getBranchId()) : null;
        String qr = !transferring ? null
            : customQr != null ? customQr
            : account != null ? Sepay.qrUrl(account, order.getTotalAmount(), order.getCode())
            : null;
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
            account, account != null && sepay.enabled(), order.getPickupAt());
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
