package com.savoris.service;

import com.savoris.event.KitchenQueueChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KitchenQueueEvents {

    private final ApplicationEventPublisher eventPublisher;

    public void changed(Integer branchId) {
        eventPublisher.publishEvent(new KitchenQueueChangedEvent(branchId));
    }
}
