package com.savoris.service.impl;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.VoidResolution;
import com.savoris.constant.OrderStatus;
import com.savoris.constant.OrderType;
import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import com.savoris.constant.StaffRole;
import com.savoris.constant.TableStatus;
import com.savoris.dto.request.AddOrderItemsRequest;
import com.savoris.dto.request.CreateOrderRequest;
import com.savoris.dto.request.WebTakeawayRequest;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.OrderItemResponse;
import com.savoris.dto.response.OrderResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.BranchMenuItemEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.OrderItemEntity;
import com.savoris.entity.OrderItemVoidHistoryEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.AreaRepository;
import com.savoris.repository.BranchMenuItemRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.OrderItemVoidHistoryRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.repository.PaymentRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.KitchenService;
import com.savoris.service.OrderService;
import com.savoris.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final List<OrderItemStatus> UNFINISHED_ITEM_STATUSES =
        List.of(OrderItemStatus.PENDING, OrderItemStatus.COOKING, OrderItemStatus.VOID_REQUESTED, OrderItemStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemVoidHistoryRepository orderItemVoidHistoryRepository;
    private final BranchRepository branchRepository;
    private final MenuItemRepository menuItemRepository;
    private final BranchMenuItemRepository branchMenuItemRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final OrderCodeGenerator orderCodeGenerator;
    private final KitchenService kitchenService;
    private final PaymentRepository paymentRepository;
    private final DiningTableRepository diningTableRepository;
    private final StaffRepository staffRepository;
    private final SettingService settingService;
    private final AreaRepository areaRepository;
    private final KitchenQueueEvents kitchenQueueEvents;

    @Override
    @Transactional
    public OrderResponse createOrder(Integer staffId, CreateOrderRequest request) {
        Integer branchId = request.getBranchId();
        branchAccessGuard.check(staffId, branchId);

        boolean dineIn = request.getOrderType() == OrderType.DINE_IN;
        BranchEntity branch = (dineIn ? branchRepository.findById(branchId) : branchRepository.findByIdForUpdate(branchId))
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Chi nhánh " + branch.getCode() + " không hoạt động");
        }
        if (dineIn && request.getTableId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Đơn tại bàn cần chọn bàn");
        }
        if (dineIn && diningTableRepository.findInBranch(request.getTableId(), branchId).isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Bàn " + request.getTableId() + " không thuộc chi nhánh này");
        }

        if (!dineIn) {
            checkKitchenAcceptsTakeaway(branch);
        }

        List<OrderItemEntity> items = buildItems(branchId, request.getItems());
        BigDecimal subtotal = items.stream().map(OrderItemEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderEntity order = new OrderEntity();
        order.setCode(orderCodeGenerator.next(branchId));
        order.setBranchId(branchId);
        order.setType(request.getOrderType());
        order.setTableId(request.getTableId());
        order.setStatus(OrderStatus.OPEN);
        order.setOpenedBy(staffId);
        order.setNote(request.getNotes());
        price(order, subtotal);
        OrderEntity savedOrder = orderRepository.save(order);

        items.forEach(item -> item.setOrderId(savedOrder.getId()));
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(items);
        if (dineIn) {
            diningTableRepository.updateStatus(savedOrder.getTableId(), TableStatus.OCCUPIED);
        }

        kitchenQueueEvents.changed(branchId);
        log.info("Created order {} for branch {}", savedOrder.getCode(), branchId);
        return toResponse(savedOrder, savedItems);
    }

    // total = subtotal + VAT
    private void price(OrderEntity order, BigDecimal subtotal) {
        BigDecimal vat = subtotal.multiply(settingService.getVatPercent()).divide(HUNDRED, 0, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal);
        order.setVatAmount(vat);
        order.setTotalAmount(subtotal.add(vat));
    }

    private void recalculate(OrderEntity order) {
        BigDecimal subtotal = orderItemRepository.findByOrderId(order.getId()).stream()
            .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
            .map(OrderItemEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        price(order, subtotal);
        orderRepository.save(order);
    }

    private void checkKitchenAcceptsTakeaway(BranchEntity branch) {
        if (Boolean.TRUE.equals(branch.getTakeawayPaused())) {
            throw new AppException(ErrorCode.BRANCH_BUSY, "Chi nhánh tạm ngưng nhận đơn mang về.");
        }
        BranchLoadResponse load = kitchenService.computeLoad(branch);
        if (!load.isAcceptingTakeaway()) {
            String wait = load.getEstimatedWaitMinutes() != null
                ? " Thời gian chờ ước tính: " + load.getEstimatedWaitMinutes() + " phút."
                : "";
            throw new AppException(ErrorCode.BRANCH_BUSY, ErrorCode.BRANCH_BUSY.getMessage() + "." + wait);
        }
    }

    @Override
    @Transactional
    public OrderResponse addItems(Integer staffId, Integer orderId, AddOrderItemsRequest request) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.ALREADY_PAID, "Đơn " + order.getCode() + " đã thanh toán: mở đơn mới để gọi thêm");
        }
        if (order.getType() != OrderType.DINE_IN) {
            checkKitchenAcceptsTakeaway(branchRepository.findByIdForUpdate(order.getBranchId())
                .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND)));
        }
        List<OrderItemEntity> items = buildItems(order.getBranchId(), request.getItems());
        items.forEach(item -> item.setOrderId(orderId));
        orderItemRepository.saveAll(items);
        recalculate(order);

        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Added {} dishes to order {}", items.size(), order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse placeGuestOrder(String qrToken, AddOrderItemsRequest request) {
        DiningTableEntity table = diningTableRepository.findByQrTokenForUpdate(qrToken)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Mã QR không hợp lệ"));
        Integer branchId = areaRepository.findById(table.getAreaId()).orElseThrow().getBranchId();
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Chi nhánh " + branch.getCode() + " không nhận đơn");
        }

        OrderEntity order = orderRepository.findFirstByTableIdAndStatusOrderByIdDesc(table.getId(), OrderStatus.OPEN)
            .filter(open -> !paymentRepository.existsByOrderIdAndStatus(open.getId(), PaymentStatus.PAID))
            .orElse(null);
        List<OrderItemEntity> items = buildItems(branchId, request.getItems());
        if (order == null) {
            order = new OrderEntity();
            order.setCode(orderCodeGenerator.next(branchId));
            order.setBranchId(branchId);
            order.setType(OrderType.DINE_IN);
            order.setTableId(table.getId());
            order.setStatus(OrderStatus.OPEN);
            price(order, BigDecimal.ZERO);
            order = orderRepository.save(order);
            diningTableRepository.updateStatus(table.getId(), TableStatus.OCCUPIED);
        }
        Integer orderId = order.getId();
        items.forEach(item -> {
            item.setOrderId(orderId);
            item.setOrderedByGuest(true);
        });
        orderItemRepository.saveAll(items);
        recalculate(order);

        kitchenQueueEvents.changed(branchId);
        log.info("Guest at table {} ordered {} dishes on {}", table.getName(), items.size(), order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public String placeWebTakeaway(WebTakeawayRequest request) {
        BranchEntity branch = branchRepository.findByIdForUpdate(request.branchId())
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Chi nhánh " + branch.getCode() + " không nhận đơn");
        }
        if (request.payMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        if (!settingService.transferAvailable(branch.getId())) {
            throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED, "Chi nhánh chưa thiết lập thanh toán chuyển khoản cho đơn mang về.");
        }
        checkKitchenAcceptsTakeaway(branch);

        List<OrderItemEntity> items = buildItems(branch.getId(), request.items());
        OrderEntity order = new OrderEntity();
        order.setCode(orderCodeGenerator.next(branch.getId()));
        order.setBranchId(branch.getId());
        order.setType(OrderType.TAKEAWAY);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setGuestName(request.guestName().trim());
        order.setGuestPhone(request.guestPhone().trim());
        order.setPickupAt(request.pickupAt());
        order.setPayMethod(request.payMethod());
        order.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        order.setNote(request.notes());
        price(order, items.stream().map(OrderItemEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        OrderEntity saved = orderRepository.save(order);
        items.forEach(item -> item.setOrderId(saved.getId()));
        orderItemRepository.saveAll(items);

        log.info("Web takeaway {} at branch {} waits for {} payment", saved.getCode(), branch.getCode(), request.payMethod());
        return saved.getPublicToken();
    }

    @Override
    @Transactional
    public OrderResponse cancelItem(Integer staffId, Integer orderId, Integer itemId, String reason) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Đơn " + order.getCode() + " đã thanh toán: hoàn tiền trước khi thay đổi");
        }
        OrderItemEntity item = orderItemRepository.findById(itemId)
            .filter(i -> i.getOrderId().equals(orderId))
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Món " + itemId + " không thuộc đơn " + order.getCode()));

        OrderItemStatus from = item.getStatus();
        if (from == OrderItemStatus.SERVED || from == OrderItemStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " đã ở trạng thái " + from);
        }
        if (from != OrderItemStatus.PENDING) {
            StaffRole role = staffRepository.findById(staffId).orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)).getRole();
            if (role != StaffRole.ADMIN && role != StaffRole.MANAGER) {
                throw new AppException(ErrorCode.FORBIDDEN, item.getItemName() + " đang nấu: nhờ quản lý hủy");
            }
        }
        if (orderItemRepository.transition(itemId, from, OrderItemStatus.CANCELLED, LocalDateTime.now()) == 0) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " đã thay đổi, thử lại");
        }
        OrderItemEntity cancelled = orderItemRepository.findById(itemId).orElseThrow();
        cancelled.setCancelReason(reason);
        orderItemRepository.save(cancelled);
        recalculate(order);

        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Cancelled {} on order {} ({})", item.getItemName(), order.getCode(), reason);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse requestVoid(Integer staffId, Integer orderId, Integer itemId, String reason) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        OrderItemEntity item = orderItemRepository.findById(itemId)
            .filter(i -> i.getOrderId().equals(orderId))
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Món " + itemId + " không thuộc đơn " + order.getCode()));

        OrderItemStatus from = item.getStatus();
        if (from == OrderItemStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE, "Món chưa vào bếp — dùng nút huỷ trực tiếp");
        }
        if (from == OrderItemStatus.SERVED || from == OrderItemStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " đã " + from + ", không thể yêu cầu hủy");
        }
        if (from == OrderItemStatus.VOID_REQUESTED) {
            throw new AppException(ErrorCode.INVALID_STATE, "Yêu cầu hủy đã được gửi, đang chờ bếp xử lý");
        }

        if (orderItemRepository.transition(itemId, from, OrderItemStatus.VOID_REQUESTED, LocalDateTime.now()) == 0) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " đã thay đổi, thử lại");
        }
        OrderItemEntity updated = orderItemRepository.findById(itemId).orElseThrow();
        updated.setVoidReason(reason);
        updated.setVoidRequestedBy(staffId);
        updated.setVoidPreviousStatus(from);
        updated.setVoidRequestedAt(LocalDateTime.now());
        updated.setVoidResolution(null);
        orderItemRepository.save(updated);
        OrderItemVoidHistoryEntity history = new OrderItemVoidHistoryEntity();
        history.setOrderId(orderId);
        history.setOrderItemId(itemId);
        history.setRequestedBy(staffId);
        history.setRequestedAt(updated.getVoidRequestedAt());
        history.setRequestReason(reason);
        history.setPreviousStatus(from);
        orderItemVoidHistoryRepository.save(history);

        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Void requested for item {} on order {} by staff {}: {}", itemId, order.getCode(), staffId, reason);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse resolveVoid(Integer staffId, Integer orderId, Integer itemId, VoidResolution resolution) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn: " + orderId));
        branchAccessGuard.check(staffId, order.getBranchId());

        StaffRole role = staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)).getRole();
        if (role != StaffRole.ADMIN && role != StaffRole.MANAGER && role != StaffRole.CHEF) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ đầu bếp hoặc quản lý mới có thể xử lý yêu cầu hủy");
        }

        OrderItemEntity item = orderItemRepository.findById(itemId)
            .filter(i -> i.getOrderId().equals(orderId))
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Món không thuộc đơn này"));
        if (item.getStatus() != OrderItemStatus.VOID_REQUESTED) {
            throw new AppException(ErrorCode.INVALID_STATE, "Món " + itemId + " không ở trạng thái VOID_REQUESTED");
        }

        LocalDateTime now = LocalDateTime.now();
        switch (resolution) {
            case ACCEPT -> {
                if (orderItemRepository.transition(itemId, OrderItemStatus.VOID_REQUESTED, OrderItemStatus.CANCELLED, now) == 0) {
                    throw new AppException(ErrorCode.INVALID_STATE, "Xung đột cập nhật đồng thời");
                }
                OrderItemEntity accepted = orderItemRepository.findById(itemId).orElseThrow();
                accepted.setVoidResolution(VoidResolution.ACCEPT);
                accepted.setCancelReason("Bếp chấp nhận hủy" + (item.getVoidReason() != null ? ": " + item.getVoidReason() : ""));
                orderItemRepository.save(accepted);
                recalculate(order);
            }
            case REJECT -> {
                OrderItemStatus previous = item.getVoidPreviousStatus();
                if (previous != OrderItemStatus.COOKING && previous != OrderItemStatus.READY) {
                    throw new AppException(ErrorCode.INVALID_STATE, "Không thể khôi phục trạng thái trước yêu cầu hủy");
                }
                if (orderItemRepository.transition(itemId, OrderItemStatus.VOID_REQUESTED, previous, now) == 0) {
                    throw new AppException(ErrorCode.INVALID_STATE, "Xung đột cập nhật đồng thời");
                }
                OrderItemEntity rejected = orderItemRepository.findById(itemId).orElseThrow();
                rejected.setVoidResolution(VoidResolution.REJECT);
                rejected.setVoidPreviousStatus(null);
                orderItemRepository.save(rejected);
            }
            case REMAKE -> {
                if (orderItemRepository.transition(itemId, OrderItemStatus.VOID_REQUESTED, OrderItemStatus.CANCELLED, now) == 0) {
                    throw new AppException(ErrorCode.INVALID_STATE, "Xung đột cập nhật đồng thời");
                }
                OrderItemEntity cancelled = orderItemRepository.findById(itemId).orElseThrow();
                cancelled.setVoidResolution(VoidResolution.REMAKE);
                cancelled.setCancelReason("Làm lại miễn phí");
                orderItemRepository.save(cancelled);
                OrderItemEntity remake = new OrderItemEntity();
                remake.setOrderId(orderId);
                remake.setMenuItemId(item.getMenuItemId());
                remake.setItemName(item.getItemName());
                remake.setUnitPrice(item.getUnitPrice());
                remake.setQuantity(item.getQuantity());
                remake.setAmount(item.getAmount());
                remake.setNote("[Làm lại] " + (item.getVoidReason() != null ? item.getVoidReason() : ""));
                remake.setNeedsKitchen(true);
                remake.setStatus(OrderItemStatus.PENDING);
                orderItemRepository.save(remake);
                recalculate(order);
            }
            case CHARGE -> {
                if (orderItemRepository.transition(itemId, OrderItemStatus.VOID_REQUESTED, OrderItemStatus.READY, now) == 0) {
                    throw new AppException(ErrorCode.INVALID_STATE, "Xung đột cập nhật đồng thời");
                }
                OrderItemEntity charged = orderItemRepository.findById(itemId).orElseThrow();
                charged.setVoidResolution(VoidResolution.CHARGE);
                charged.setReadyAt(now);
                orderItemRepository.save(charged);
            }
        }

        OrderItemVoidHistoryEntity history = orderItemVoidHistoryRepository
            .findFirstByOrderItemIdAndResolvedAtIsNullOrderByRequestedAtDesc(itemId)
            .orElseThrow(() -> new AppException(ErrorCode.INVALID_STATE, "Không tìm thấy lịch sử yêu cầu hủy đang chờ"));
        history.setResolution(resolution);
        history.setResolvedBy(staffId);
        history.setResolvedAt(now);
        orderItemVoidHistoryRepository.save(history);

        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Void resolved ({}) for item {} on order {} by staff {}", resolution, itemId, order.getCode(), staffId);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    private List<OrderItemEntity> buildItems(Integer branchId, List<CreateOrderRequest.OrderItemRequest> requests) {
        List<Integer> menuItemIds = requests.stream().map(CreateOrderRequest.OrderItemRequest::getMenuItemId).distinct().toList();
        Map<Integer, MenuItemEntity> menuItems = menuItemRepository.findAllById(menuItemIds).stream()
            .collect(Collectors.toMap(MenuItemEntity::getId, Function.identity()));
        Map<Integer, BranchMenuItemEntity> branchOverrides = branchMenuItemRepository
            .findByBranchIdAndMenuItemIdIn(branchId, menuItemIds).stream()
            .collect(Collectors.toMap(BranchMenuItemEntity::getMenuItemId, Function.identity()));

        List<OrderItemEntity> items = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest itemReq : requests) {
            MenuItemEntity menuItem = menuItems.get(itemReq.getMenuItemId());
            if (menuItem == null || !Boolean.TRUE.equals(menuItem.getIsActive()) || menuItem.getDeletedAt() != null) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, "Món không còn trên thực đơn: " + itemReq.getMenuItemId());
            }
            BranchMenuItemEntity override = branchOverrides.get(menuItem.getId());
            if (override != null && !Boolean.TRUE.equals(override.getOffered())) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, menuItem.getName() + " không bán tại chi nhánh này");
            }
            if (override != null && !Boolean.TRUE.equals(override.getIsAvailable())) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, menuItem.getName() + " tạm hết tại chi nhánh này");
            }
            BigDecimal unitPrice = override != null && override.getPrice() != null ? override.getPrice() : menuItem.getBasePrice();

            OrderItemEntity item = new OrderItemEntity();
            item.setMenuItemId(menuItem.getId());
            item.setItemName(menuItem.getName());
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemReq.getQuantity());
            item.setAmount(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            item.setNote(itemReq.getNotes());
            boolean kitchen = !Boolean.FALSE.equals(menuItem.getNeedsKitchen());
            item.setNeedsKitchen(kitchen);
            item.setStatus(kitchen ? OrderItemStatus.PENDING : OrderItemStatus.READY);
            if (!kitchen) {
                item.setReadyAt(LocalDateTime.now());
            }
            items.add(item);
        }
        return items;
    }

    @Override
    @Transactional
    public int cancelExpiredWebTakeaways(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        int cancelled = 0;
        for (OrderEntity stale : orderRepository.findExpiredWebTakeaways(cutoff)) {
            OrderEntity order = orderRepository.findByIdForUpdate(stale.getId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getTransferClaimedAt() != null
                || paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PAID)) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            orderItemRepository.cancelByOrderId(order.getId(), UNFINISHED_ITEM_STATUSES, now);
            order.setStatus(OrderStatus.CANCELLED);
            order.setClosedAt(now);
            order.setCancelReason("Quá " + minutes + " phút chưa thanh toán");
            orderRepository.save(order);
            cancelled++;
            log.info("Web takeaway {} cancelled: not paid within {} minutes", order.getCode(), minutes);
        }
        return cancelled;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Integer staffId, Integer orderId) {
        OrderEntity order = findOrder(orderId);
        branchAccessGuard.check(staffId, order.getBranchId());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(Integer staffId, Integer orderId) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        if (orderItemRepository.countByOrderIdAndStatusIn(orderId, UNFINISHED_ITEM_STATUSES) > 0) {
            throw new AppException(ErrorCode.INVALID_STATE, "Đơn " + order.getCode() + " vẫn còn món đang trong bếp");
        }
        if (!paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.ORDER_NOT_PAID, "Đơn " + order.getCode() + " chưa thanh toán");
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setClosedBy(staffId);
        order.setClosedAt(LocalDateTime.now());
        orderRepository.save(order);
        releaseTable(order);

        log.info("Completed order {}", order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Integer staffId, Integer orderId) {
        OrderEntity order = findOrderForUpdate(staffId, orderId, OrderStatus.OPEN, OrderStatus.AWAITING_PAYMENT);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Đơn " + order.getCode() + " đã thanh toán: hoàn tiền trước khi hủy");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getPayMethod() == PaymentMethod.BANK_TRANSFER
            && order.getTransferClaimedAt() != null) {
            order.setRefundDueAt(now);
        }
        orderItemRepository.cancelByOrderId(orderId, UNFINISHED_ITEM_STATUSES, now);

        order.setStatus(OrderStatus.CANCELLED);
        order.setClosedBy(staffId);
        order.setClosedAt(now);
        orderRepository.save(order);
        releaseTable(order);

        kitchenQueueEvents.changed(order.getBranchId());
        log.info("Cancelled order {}", order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByBranch(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return toResponses(orderRepository.findByBranchIdAndStatusInOrderByIdAsc(branchId,
            List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.OPEN)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getRefundsDue(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return toResponses(orderRepository.findByBranchIdAndRefundDueAtIsNotNullAndRefundSettledAtIsNullOrderByIdAsc(branchId));
    }

    @Override
    @Transactional
    public OrderResponse settleRefund(Integer staffId, Integer orderId) {
        OrderEntity order = findOrderForUpdate(staffId, orderId, OrderStatus.CANCELLED);
        if (order.getRefundDueAt() == null || order.getRefundSettledAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATE, "Đơn " + order.getCode() + " không có khoản hoàn tiền nào đang chờ");
        }
        order.setRefundSettledAt(LocalDateTime.now());
        order.setRefundSettledBy(staffId);
        orderRepository.save(order);
        log.info("Refund for cancelled order {} settled by staff {}", order.getCode(), staffId);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    private OrderEntity findOrder(Integer orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn: " + orderId));
    }

    private OrderEntity findOpenOrder(Integer staffId, Integer orderId) {
        return findOrderForUpdate(staffId, orderId, OrderStatus.OPEN);
    }

    private OrderEntity findOrderForUpdate(Integer staffId, Integer orderId, OrderStatus... allowed) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn: " + orderId));
        branchAccessGuard.check(staffId, order.getBranchId());
        if (!List.of(allowed).contains(order.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE, "Đơn " + order.getCode() + " đã ở trạng thái " + order.getStatus());
        }
        return order;
    }

    private void releaseTable(OrderEntity order) {
        if (order.getTableId() != null && !orderRepository.existsByTableIdAndStatus(order.getTableId(), OrderStatus.OPEN)) {
            diningTableRepository.updateStatus(order.getTableId(), TableStatus.AVAILABLE);
        }
    }

    private List<OrderResponse> toResponses(List<OrderEntity> orders) {
        Map<Integer, List<OrderItemEntity>> itemsByOrder = orderItemRepository
            .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
            .map(order -> toResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    private OrderResponse toResponse(OrderEntity order, List<OrderItemEntity> items) {
        return OrderResponse.from(order, items.stream().map(OrderItemResponse::from).toList());
    }
}
