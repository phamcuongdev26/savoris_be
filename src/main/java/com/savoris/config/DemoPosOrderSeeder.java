package com.savoris.config;

import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.OrderStatus;
import com.savoris.constant.OrderType;
import com.savoris.constant.PaymentMethod;
import com.savoris.constant.PaymentStatus;
import com.savoris.constant.TableStatus;
import com.savoris.entity.BranchMenuItemEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.entity.MenuItemEntity;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.OrderItemEntity;
import com.savoris.entity.PaymentEntity;
import com.savoris.repository.BranchMenuItemRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import com.savoris.repository.MenuItemRepository;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Order-derived table states for local/demo POS review. Does not call payment or notification services. */
@Configuration
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DemoPosOrderSeeder {
    private final BranchRepository branchRepository;
    private final DiningTableRepository tableRepository;
    private final BranchMenuItemRepository branchMenuRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${savoris.vat-percent:8}")
    private BigDecimal vatPercent;

    @Value("${savoris.demo.reset-pos-orders:false}")
    private boolean resetDemoOrders;

    private static final Map<String, Mix> MIX = Map.of(
        "CN01", new Mix(15, 6, 4, 2),
        "CN02", new Mix(13, 5, 4, 2),
        "CN03", new Mix(13, 5, 4, 2),
        "CN04", new Mix(8, 3, 2, 1)
    );

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    ApplicationRunner seedDemoPosOrders() {
        return args -> transactionTemplate.executeWithoutResult(status -> seedData());
    }

    private void seedData() {
        if (resetDemoOrders) resetDemoOrders();
        MIX.forEach((branchCode, mix) -> branchRepository.findByCode(branchCode).ifPresent(branch -> {
            List<DiningTableEntity> tables = tableRepository.findByBranchId(branch.getId());
            List<MenuItemChoice> menu = branchMenuRepository.findByBranchId(branch.getId()).stream()
                .filter(BranchMenuItemEntity::getOffered)
                .filter(BranchMenuItemEntity::getIsAvailable)
                .map(this::toKitchenChoice)
                .filter(choice -> choice != null)
                .toList();
            if (menu.isEmpty()) {
                log.warn("Skipping demo POS orders for {} because no available kitchen item exists", branchCode);
                return;
            }
            seedStates(branch.getId(), branchCode, tables, menu.get(0), mix);
        }));
    }

    private void resetDemoOrders() {
        for (String branchCode : MIX.keySet()) {
            for (OrderEntity order : orderRepository.findByCodeStartingWithOrderByIdAsc("DEMO-%s-".formatted(branchCode))) {
                Integer tableId = order.getTableId();
                paymentRepository.deleteByOrderId(order.getId());
                orderItemRepository.deleteByOrderId(order.getId());
                orderRepository.delete(order);
                if (tableId != null && !orderRepository.existsByTableIdAndStatus(tableId, OrderStatus.OPEN)
                    && !orderRepository.existsByTableIdAndStatus(tableId, OrderStatus.AWAITING_PAYMENT)) {
                    tableRepository.findById(tableId).ifPresent(table -> {
                        if (table.getStatus() == TableStatus.OCCUPIED) {
                            table.setStatus(TableStatus.AVAILABLE);
                            tableRepository.save(table);
                        }
                    });
                }
            }
        }
        log.info("Reset demo POS orders for branches {}", MIX.keySet());
    }

    private MenuItemChoice toKitchenChoice(BranchMenuItemEntity branchMenuItem) {
        MenuItemEntity item = menuItemRepository.findById(branchMenuItem.getMenuItemId()).orElse(null);
        if (item == null || !item.getIsActive() || item.getDeletedAt() != null || !item.getNeedsKitchen()) return null;
        BigDecimal price = branchMenuItem.getPrice() == null ? item.getBasePrice() : branchMenuItem.getPrice();
        return new MenuItemChoice(item, price);
    }

    private void seedStates(Integer branchId, String branchCode, List<DiningTableEntity> tables, MenuItemChoice menu, Mix mix) {
        int index = 0;
        for (StateKind kind : List.of(StateKind.SERVING, StateKind.READY, StateKind.WAITING_PAYMENT)) {
            int count = switch (kind) {
                case SERVING -> mix.serving();
                case READY -> mix.ready();
                case WAITING_PAYMENT -> mix.waitingPayment();
                case RESERVED -> mix.reserved();
            };
            for (int i = 0; i < count && index < tables.size(); i++, index++) {
                DiningTableEntity table = tables.get(index);
                seedOrder(branchId, branchCode, index + 1, table, menu, kind);
            }
        }
        for (int i = 0; i < mix.reserved() && index < tables.size(); i++, index++) {
            DiningTableEntity table = tables.get(index);
            String code = demoCode(branchCode, index + 1);
            if (orderRepository.findByCode(code).isPresent() || orderRepository.existsByTableId(table.getId())) continue;
            table.setStatus(TableStatus.RESERVED);
            tableRepository.save(table);
        }
    }

    private void seedOrder(Integer branchId, String branchCode, int sequence, DiningTableEntity table, MenuItemChoice menu, StateKind kind) {
        String code = demoCode(branchCode, sequence);
        if (orderRepository.findByCode(code).isPresent() || orderRepository.existsByTableId(table.getId())) return;

        LocalDateTime now = LocalDateTime.now();
        BigDecimal subtotal = menu.price();
        BigDecimal vat = subtotal.multiply(vatPercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(vat);
        OrderEntity order = new OrderEntity();
        order.setCode(code);
        order.setBranchId(branchId);
        order.setType(OrderType.DINE_IN);
        order.setTableId(table.getId());
        order.setGuestCount(Math.min(table.getCapacity() == null ? 2 : table.getCapacity(), 4));
        order.setStatus(OrderStatus.OPEN);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setSubtotal(subtotal);
        order.setVatAmount(vat);
        order.setTotalAmount(total);
        order.setNote("DỮ LIỆU DEMO — không thuộc giao dịch thực");
        OrderEntity savedOrder = orderRepository.save(order);

        OrderItemStatus itemStatus = switch (kind) {
            case SERVING -> OrderItemStatus.PENDING;
            case READY -> OrderItemStatus.READY;
            case WAITING_PAYMENT -> OrderItemStatus.SERVED;
            case RESERVED -> throw new IllegalArgumentException("Reserved tables do not have demo orders");
        };
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.setOrderId(savedOrder.getId());
        orderItem.setMenuItemId(menu.item().getId());
        orderItem.setItemName(menu.item().getName());
        orderItem.setUnitPrice(menu.price());
        orderItem.setQuantity(1);
        orderItem.setAmount(menu.price());
        orderItem.setStatus(itemStatus);
        orderItem.setNeedsKitchen(true);
        orderItem.setNote("Dữ liệu demo POS");
        if (kind == StateKind.READY || kind == StateKind.WAITING_PAYMENT) orderItem.setReadyAt(now);
        orderItemRepository.save(orderItem);

        if (kind == StateKind.WAITING_PAYMENT) {
            PaymentEntity payment = new PaymentEntity();
            payment.setOrderId(savedOrder.getId());
            payment.setMethod(PaymentMethod.CASH);
            payment.setAmount(total);
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
        }
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);
    }

    private static String demoCode(String branchCode, int sequence) {
        return "DEMO-%s-%03d".formatted(branchCode, sequence);
    }

    private enum StateKind { SERVING, READY, WAITING_PAYMENT, RESERVED }
    private record MenuItemChoice(MenuItemEntity item, BigDecimal price) { }
    private record Mix(int serving, int ready, int waitingPayment, int reserved) { }
}
