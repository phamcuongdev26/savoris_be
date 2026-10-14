package com.savoris.notification;

import com.savoris.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Once a minute: web takeaway still unpaid after the time limit is cancelled so it stops cluttering the counter
@Component
@RequiredArgsConstructor
public class TakeawayExpiryJob {

    private final OrderService orderService;

    @Value("${savoris.takeaway.payment-timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cancelUnpaid() {
        orderService.cancelExpiredWebTakeaways(timeoutMinutes);
    }
}
