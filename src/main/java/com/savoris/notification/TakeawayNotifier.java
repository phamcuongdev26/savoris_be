package com.savoris.notification;

import com.savoris.constant.OrderItemStatus;
import com.savoris.constant.OrderStatus;
import com.savoris.entity.OrderEntity;
import com.savoris.entity.PushSubscriptionEntity;
import com.savoris.repository.OrderItemRepository;
import com.savoris.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tells a web takeaway guest their food is ready, on their phone, even with the page closed (browser push).
 * Sent after the transaction commits and off the request thread: a slow push service never holds up the kitchen.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TakeawayNotifier {

    private static final List<OrderItemStatus> STILL_COOKING = List.of(OrderItemStatus.PENDING, OrderItemStatus.COOKING);

    private final OrderItemRepository orderItemRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushSender webPushSender;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // Call when a dish of the order became ready or the order was just paid: pushes once nothing is left to cook
    public void notifyIfReady(OrderEntity order) {
        if (order.getPublicToken() == null || order.getStatus() != OrderStatus.OPEN
            || orderItemRepository.countByOrderIdAndStatusIn(order.getId(), STILL_COOKING) > 0) {
            return;
        }
        List<PushSubscriptionEntity> subscriptions = pushSubscriptionRepository.findByOrderId(order.getId());
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = "{\"title\":\"Món đã xong!\",\"body\":\"Đơn " + order.getCode()
            + " đã sẵn sàng, mời bạn nhận tại quầy.\",\"url\":\"/don-mang-ve/" + order.getPublicToken() + "\"}";
        Runnable send = () -> subscriptions.forEach(s -> push(s, payload));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.submit(send);
                }
            });
        } else {
            executor.submit(send);
        }
    }

    private void push(PushSubscriptionEntity s, String payload) {
        try {
            int status = webPushSender.send(s.getEndpoint(), s.getP256dh(), s.getAuth(), payload);
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.delete(s);   // the browser unsubscribed
            } else if (status >= 300) {
                log.warn("Push to order {} answered {}", s.getOrderId(), status);
            }
        } catch (Exception e) {
            log.warn("Push to order {} failed: {}", s.getOrderId(), e.getMessage());
        }
    }
}
