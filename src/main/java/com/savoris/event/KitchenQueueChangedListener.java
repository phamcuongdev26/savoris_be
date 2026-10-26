package com.savoris.event;

import com.savoris.service.KitchenEventStreamRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class KitchenQueueChangedListener {

    private final KitchenEventStreamRegistry streamRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueueChanged(KitchenQueueChangedEvent event) {
        streamRegistry.queueChanged(event.branchId());
    }
}
