package com.dinechain.service.impl;

import com.dinechain.constant.BranchStatus;
import com.dinechain.constant.OrderItemStatus;
import com.dinechain.constant.OrderStatus;
import com.dinechain.constant.OrderType;
import com.dinechain.constant.PaymentMethod;
import com.dinechain.constant.PaymentStatus;
import com.dinechain.constant.StaffRole;
import com.dinechain.constant.TableStatus;
import com.dinechain.dto.request.AddOrderItemsRequest;
import com.dinechain.dto.request.CreateOrderRequest;
import com.dinechain.dto.request.WebTakeawayRequest;
import com.dinechain.dto.response.BranchLoadResponse;
import com.dinechain.dto.response.OrderItemResponse;
import com.dinechain.dto.response.OrderResponse;
import com.dinechain.entity.BranchEntity;
import com.dinechain.entity.BranchMenuItemEntity;
import com.dinechain.entity.DiningTableEntity;
import com.dinechain.entity.MenuItemEntity;
import com.dinechain.entity.OrderEntity;
import com.dinechain.entity.OrderItemEntity;
import com.dinechain.entity.PromotionEntity;
import com.dinechain.entity.StaffEntity;
import com.dinechain.exception.AppException;
import com.dinechain.exception.ErrorCode;
import com.dinechain.repository.AreaRepository;
import com.dinechain.repository.BranchMenuItemRepository;
import com.dinechain.repository.BranchRepository;
import com.dinechain.repository.CustomerRepository;
import com.dinechain.repository.DiningTableRepository;
import com.dinechain.repository.MenuItemRepository;
import com.dinechain.repository.OrderItemRepository;
import com.dinechain.repository.OrderRepository;
import com.dinechain.repository.PaymentRepository;
import com.dinechain.repository.StaffRepository;
import com.dinechain.security.BranchAccessGuard;
import com.dinechain.service.InventoryService;
import com.dinechain.service.KitchenService;
import com.dinechain.service.LoyaltyService;
import com.dinechain.service.OrderCodeGenerator;
import com.dinechain.service.OrderService;
import com.dinechain.service.PromotionService;
import com.dinechain.service.SettingService;
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

    // Items the kitchen has not handed over yet
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final List<OrderItemStatus> UNFINISHED_ITEM_STATUSES =
        List.of(OrderItemStatus.PENDING, OrderItemStatus.COOKING, OrderItemStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BranchRepository branchRepository;
    private final MenuItemRepository menuItemRepository;
    private final BranchMenuItemRepository branchMenuItemRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final OrderCodeGenerator orderCodeGenerator;
    private final KitchenService kitchenService;
    private final PaymentRepository paymentRepository;
    private final DiningTableRepository diningTableRepository;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final PromotionService promotionService;
    private final LoyaltyService loyaltyService;
    private final SettingService settingService;
    private final AreaRepository areaRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public OrderResponse createOrder(Integer staffId, CreateOrderRequest request) {
        Integer branchId = request.getBranchId();
        branchAccessGuard.check(staffId, branchId);

        boolean dineIn = request.getOrderType() == OrderType.DINE_IN;
        // Takeaway & delivery lock the branch row so concurrent orders cannot all pass the "kitchen full?" check at once
        BranchEntity branch = (dineIn ? branchRepository.findById(branchId) : branchRepository.findByIdForUpdate(branchId))
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Branch " + branch.getCode() + " is not active");
        }
        if (dineIn && request.getTableId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Dine-in order requires a table");
        }
        if (dineIn && diningTableRepository.findInBranch(request.getTableId(), branchId).isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Table " + request.getTableId() + " does not belong to this branch");
        }
        if (request.getCustomerId() != null && !customerRepository.existsById(request.getCustomerId())) {
            throw new AppException(ErrorCode.NOT_FOUND, "Customer not found: " + request.getCustomerId());
        }

        // Peak hours: guests already seated are always served, takeaway & delivery wait until the kitchen catches up
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
        order.setCustomerId(request.getCustomerId());
        order.setStatus(OrderStatus.OPEN);
        order.setOpenedBy(staffId);
        order.setNote(request.getNotes());
        applyPricing(order, subtotal, request);
        OrderEntity savedOrder = orderRepository.save(order);
        if (savedOrder.getPointsUsed() > 0) {
            loyaltyService.spend(savedOrder.getCustomerId(), savedOrder.getPointsUsed(), savedOrder);
        }

        items.forEach(item -> item.setOrderId(savedOrder.getId()));
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(items);
        handOutReadyMade(branchId, savedItems, staffId);
        if (dineIn) {
            diningTableRepository.updateStatus(savedOrder.getTableId(), TableStatus.OCCUPIED);
        }

        log.info("Created order {} for branch {}", savedOrder.getCode(), branchId);
        return toResponse(savedOrder, savedItems);
    }

    private void applyPricing(OrderEntity order, BigDecimal subtotal, CreateOrderRequest request) {
        if (request.getPromotionCode() != null && !request.getPromotionCode().isBlank()) {
            PromotionEntity promotion = promotionService.redeem(request.getPromotionCode().trim(), order.getBranchId(), subtotal);
            order.setPromotionId(promotion.getId());
        }
        int points = request.getPointsToUse() != null ? request.getPointsToUse() : 0;
        if (points > 0) {
            if (order.getCustomerId() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Spending points needs a customer");
            }
            BigDecimal discount = order.getPromotionId() != null
                ? promotionService.discountFor(promotionService.get(order.getPromotionId()), subtotal)
                : BigDecimal.ZERO;
            int maxPoints = maxPoints(subtotal.subtract(discount));
            if (points > maxPoints) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "At most " + maxPoints + " points can be spent on this order");
            }
        }
        order.setPointsUsed(points);
        price(order, subtotal);
    }

    // subtotal - promotion - points = net, + VAT on the net = what the customer pays
    private void price(OrderEntity order, BigDecimal subtotal) {
        BigDecimal discount = order.getPromotionId() != null
            ? promotionService.discountFor(promotionService.get(order.getPromotionId()), subtotal)
            : BigDecimal.ZERO;
        BigDecimal pointsDiscount = loyaltyService.pointValue().multiply(BigDecimal.valueOf(order.getPointsUsed()));
        BigDecimal net = subtotal.subtract(discount).subtract(pointsDiscount);
        BigDecimal vat = net.multiply(settingService.getVatPercent()).divide(HUNDRED, 0, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setVatAmount(vat);
        order.setTotalAmount(net.add(vat));
    }

    private int maxPoints(BigDecimal payable) {
        return payable.max(BigDecimal.ZERO).divide(loyaltyService.pointValue(), 0, RoundingMode.FLOOR).intValue();
    }

    // After dishes were added or removed: the promotion and the points must still fit the new bill
    private void recalculate(OrderEntity order) {
        BigDecimal subtotal = orderItemRepository.findByOrderId(order.getId()).stream()
            .filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
            .map(OrderItemEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (order.getPromotionId() != null
            && subtotal.compareTo(promotionService.get(order.getPromotionId()).getMinOrderAmount()) < 0) {
            promotionService.release(order.getPromotionId());
            order.setPromotionId(null);
        }
        BigDecimal discount = order.getPromotionId() != null
            ? promotionService.discountFor(promotionService.get(order.getPromotionId()), subtotal)
            : BigDecimal.ZERO;
        int fits = maxPoints(subtotal.subtract(discount));
        if (order.getPointsUsed() > fits) {
            loyaltyService.giveBack(order, order.getPointsUsed() - fits);
            order.setPointsUsed(fits);
        }
        price(order, subtotal);
        orderRepository.save(order);
    }

    private void checkKitchenAcceptsTakeaway(BranchEntity branch) {
        BranchLoadResponse load = kitchenService.computeLoad(branch);
        if (!load.isAcceptingTakeaway()) {
            String wait = load.getEstimatedWaitMinutes() != null
                ? " Estimated wait: " + load.getEstimatedWaitMinutes() + " minutes."
                : "";
            throw new AppException(ErrorCode.BRANCH_BUSY, ErrorCode.BRANCH_BUSY.getMessage() + "." + wait);
        }
    }

    // Guests order another round on the same bill
    @Override
    @Transactional
    public OrderResponse addItems(Integer staffId, Integer orderId, AddOrderItemsRequest request) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.ALREADY_PAID, "Order " + order.getCode() + " is already paid: open a new order for more dishes");
        }
        if (order.getType() != OrderType.DINE_IN) {
            checkKitchenAcceptsTakeaway(branchRepository.findByIdForUpdate(order.getBranchId())
                .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND)));
        }
        List<OrderItemEntity> items = buildItems(order.getBranchId(), request.getItems());
        items.forEach(item -> item.setOrderId(orderId));
        handOutReadyMade(order.getBranchId(), orderItemRepository.saveAll(items), staffId);
        recalculate(order);

        log.info("Added {} dishes to order {}", items.size(), order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    // A guest scanned the QR on the table: the dishes join the table's open bill, or open one, and go straight to the kitchen
    @Override
    @Transactional
    public OrderResponse placeGuestOrder(String qrToken, AddOrderItemsRequest request) {
        DiningTableEntity table = diningTableRepository.findByQrTokenForUpdate(qrToken)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown table QR code"));
        Integer branchId = areaRepository.findById(table.getAreaId()).orElseThrow().getBranchId();
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Branch " + branch.getCode() + " is not taking orders");
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
            order.setPointsUsed(0);
            price(order, BigDecimal.ZERO);
            order = orderRepository.save(order);
            diningTableRepository.updateStatus(table.getId(), TableStatus.OCCUPIED);
        }
        Integer orderId = order.getId();
        items.forEach(item -> {
            item.setOrderId(orderId);
            item.setOrderedByGuest(true);
        });
        handOutReadyMade(branchId, orderItemRepository.saveAll(items), null);
        recalculate(order);

        log.info("Guest at table {} ordered {} dishes on {}", table.getName(), items.size(), order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    // Takeaway ordered on the website: priced like any order, but held as AWAITING_PAYMENT so the kitchen only
    // cooks it once the cashier took the cash or saw the transfer arrive
    @Override
    @Transactional
    public String placeWebTakeaway(WebTakeawayRequest request) {
        BranchEntity branch = branchRepository.findByIdForUpdate(request.branchId())
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATE, "Branch " + branch.getCode() + " is not taking orders");
        }
        if (request.payMethod() != PaymentMethod.CASH && request.payMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        if (request.payMethod() == PaymentMethod.BANK_TRANSFER && !settingService.transferAvailable(branch.getId())) {
            throw new AppException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED, "Bank transfer is not set up yet: pay cash at the counter");
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
        order.setPayMethod(request.payMethod());
        order.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        order.setNote(request.notes());
        order.setPointsUsed(0);
        price(order, items.stream().map(OrderItemEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        OrderEntity saved = orderRepository.save(order);
        items.forEach(item -> item.setOrderId(saved.getId()));
        handOutReadyMade(branch.getId(), orderItemRepository.saveAll(items), null);

        log.info("Web takeaway {} at branch {} waits for {} payment", saved.getCode(), branch.getCode(), request.payMethod());
        return saved.getPublicToken();
    }

    // A dish not started yet can be dropped by the floor staff; once the kitchen is on it only a manager may,
    // and a served dish cannot be taken off the bill
    @Override
    @Transactional
    public OrderResponse cancelItem(Integer staffId, Integer orderId, Integer itemId, String reason) {
        OrderEntity order = findOpenOrder(staffId, orderId);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " has been paid: refund it before changing it");
        }
        OrderItemEntity item = orderItemRepository.findById(itemId)
            .filter(i -> i.getOrderId().equals(orderId))
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Dish " + itemId + " is not on order " + order.getCode()));

        OrderItemStatus from = item.getStatus();
        if (from == OrderItemStatus.SERVED || from == OrderItemStatus.CANCELLED) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " is already " + from);
        }
        if (from != OrderItemStatus.PENDING) {
            StaffRole role = staffRepository.findById(staffId).orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)).getRole();
            if (role != StaffRole.ADMIN && role != StaffRole.BRANCH_MANAGER) {
                throw new AppException(ErrorCode.FORBIDDEN, item.getItemName() + " is already being cooked: ask a manager");
            }
        }
        // Compare-and-set: if the cook grabs the dish at this very moment, one of the two loses cleanly
        if (orderItemRepository.transition(itemId, from, OrderItemStatus.CANCELLED, LocalDateTime.now()) == 0) {
            throw new AppException(ErrorCode.INVALID_STATE, item.getItemName() + " changed in the meantime, try again");
        }
        OrderItemEntity cancelled = orderItemRepository.findById(itemId).orElseThrow();
        cancelled.setCancelReason(reason);
        orderItemRepository.save(cancelled);
        recalculate(order);

        log.info("Cancelled {} on order {} ({})", item.getItemName(), order.getCode(), reason);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    // Name and price always come from the menu, never from the client
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
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, "Menu item not available: " + itemReq.getMenuItemId());
            }
            // A dish from the chain menu is sold at every branch unless the branch turned it off
            BranchMenuItemEntity override = branchOverrides.get(menuItem.getId());
            if (override != null && !Boolean.TRUE.equals(override.getOffered())) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, menuItem.getName() + " is not sold at this branch");
            }
            if (override != null && !Boolean.TRUE.equals(override.getIsAvailable())) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, menuItem.getName() + " is sold out at this branch");
            }
            BigDecimal unitPrice = override != null && override.getPrice() != null ? override.getPrice() : menuItem.getBasePrice();

            OrderItemEntity item = new OrderItemEntity();
            item.setMenuItemId(menuItem.getId());
            item.setItemName(menuItem.getName());
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemReq.getQuantity());
            item.setAmount(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            item.setNote(itemReq.getNotes());
            // Drinks and ready-made dishes skip the kitchen: ready to hand out the moment they are ordered
            boolean kitchen = !Boolean.FALSE.equals(menuItem.getNeedsKitchen());
            item.setNeedsKitchen(kitchen);
            item.setStatus(kitchen ? OrderItemStatus.PENDING : OrderItemStatus.READY);
            if (!kitchen) {
                item.setReadyAt(LocalDateTime.now());
            }
            items.add(item);
        }
        // A ready-made dish linked to stock can only be sold while the stock lasts
        Map<Integer, Integer> readyMade = items.stream().filter(i -> !i.getNeedsKitchen())
            .collect(Collectors.groupingBy(OrderItemEntity::getMenuItemId, Collectors.summingInt(OrderItemEntity::getQuantity)));
        readyMade.forEach((menuItemId, portions) -> {
            if (!inventoryService.enoughFor(branchId, menuItemId, portions)) {
                throw new AppException(ErrorCode.MENU_ITEM_UNAVAILABLE, menuItems.get(menuItemId).getName() + " is out of stock at this branch");
            }
        });
        return items;
    }

    @Override
    @Transactional
    public int cancelExpiredWebTakeaways(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);
        int cancelled = 0;
        for (OrderEntity stale : orderRepository.findExpiredWebTakeaways(cutoff)) {
            OrderEntity order = orderRepository.findByIdForUpdate(stale.getId()).orElse(null);
            // Re-checked under the lock: the cashier may have just taken the money or the guest just said they paid
            if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getTransferClaimedAt() != null
                || paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PAID)) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            restockIfUnpaid(order);
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

    // Ready-made dishes of an order nobody paid for go back on the shelf
    private void restockIfUnpaid(OrderEntity order) {
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }
        orderItemRepository.findByOrderId(order.getId()).stream()
            .filter(i -> !i.getNeedsKitchen() && i.getStatus() != OrderItemStatus.CANCELLED)
            .forEach(i -> inventoryService.restockForDish(order.getBranchId(), i, null));
    }

    // Ready-made lines leave the stock when ordered, since no cook starts them
    private void handOutReadyMade(Integer branchId, List<OrderItemEntity> saved, Integer staffId) {
        saved.stream().filter(i -> !i.getNeedsKitchen()).forEach(i -> inventoryService.deductForDish(branchId, i, staffId));
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
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " still has dishes in the kitchen");
        }
        if (!paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.ORDER_NOT_PAID, "Order " + order.getCode() + " has not been paid yet");
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setClosedBy(staffId);
        order.setClosedAt(LocalDateTime.now());
        orderRepository.save(order);
        releaseTable(order);
        loyaltyService.earn(order);

        log.info("Completed order {}", order.getCode());
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Integer staffId, Integer orderId) {
        OrderEntity order = findOrderForUpdate(staffId, orderId, OrderStatus.OPEN, OrderStatus.AWAITING_PAYMENT);
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " has been paid: refund it before cancelling");
        }
        LocalDateTime now = LocalDateTime.now();
        // The guest said the money left their account: whoever cancels owes them a refund unless it never arrives
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getPayMethod() == PaymentMethod.BANK_TRANSFER
            && order.getTransferClaimedAt() != null) {
            order.setRefundDueAt(now);
        }
        restockIfUnpaid(order);
        // Take the dishes off the kitchen queue too
        orderItemRepository.cancelByOrderId(orderId, UNFINISHED_ITEM_STATUSES, now);

        order.setStatus(OrderStatus.CANCELLED);
        order.setClosedBy(staffId);
        order.setClosedAt(now);
        orderRepository.save(order);
        releaseTable(order);
        loyaltyService.giveBackSpent(order);
        if (order.getPromotionId() != null) {
            promotionService.release(order.getPromotionId());
        }

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
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " has no refund waiting");
        }
        order.setRefundSettledAt(LocalDateTime.now());
        order.setRefundSettledBy(staffId);
        orderRepository.save(order);
        log.info("Refund for cancelled order {} settled by staff {}", order.getCode(), staffId);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(Integer staffId, Integer customerId) {
        // Customers are shared across the chain, but staff only see the orders placed at their own branch
        StaffEntity staff = staffRepository.findById(staffId).orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        List<OrderEntity> orders = staff.getRole() == StaffRole.ADMIN
            ? orderRepository.findByCustomerId(customerId)
            : orderRepository.findByCustomerIdAndBranchId(customerId, staff.getBranchId());
        return toResponses(orders);
    }

    private OrderEntity findOrder(Integer orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + orderId));
    }

    // Row lock: complete, cancel and payment of the same order cannot interleave
    private OrderEntity findOpenOrder(Integer staffId, Integer orderId) {
        return findOrderForUpdate(staffId, orderId, OrderStatus.OPEN);
    }

    private OrderEntity findOrderForUpdate(Integer staffId, Integer orderId, OrderStatus... allowed) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Order not found: " + orderId));
        branchAccessGuard.check(staffId, order.getBranchId());
        if (!List.of(allowed).contains(order.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE, "Order " + order.getCode() + " is already " + order.getStatus());
        }
        return order;
    }

    // The table is free again once its last open order is closed
    private void releaseTable(OrderEntity order) {
        if (order.getTableId() != null && !orderRepository.existsByTableIdAndStatus(order.getTableId(), OrderStatus.OPEN)) {
            diningTableRepository.updateStatus(order.getTableId(), TableStatus.AVAILABLE);
        }
    }

    // Loads items of all orders in one query instead of one query per order
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
        return OrderResponse.from(order, items.stream().map(OrderItemResponse::from).toList(), loyaltyService.pointValue());
    }
}
