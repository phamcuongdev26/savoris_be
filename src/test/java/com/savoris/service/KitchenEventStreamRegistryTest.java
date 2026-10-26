package com.savoris.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KitchenEventStreamRegistryTest {

    @Test
    void queueChangesOnlyReachSubscribersForThatBranch() throws Exception {
        KitchenEventStreamRegistry registry = new KitchenEventStreamRegistry();
        SseEmitter branchOne = spy(new SseEmitter(0L));
        SseEmitter branchTwo = spy(new SseEmitter(0L));
        doNothing().when(branchOne).send(any(SseEmitter.SseEventBuilder.class));
        doNothing().when(branchTwo).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(1, branchOne);
        registry.register(2, branchTwo);

        registry.queueChanged(1);

        verify(branchOne, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(branchTwo, times(0)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
