package com.savoris.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class KitchenEventStreamRegistry {

    private final Map<Integer, Set<SseEmitter>> emittersByBranch = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer branchId) {
        SseEmitter emitter = new SseEmitter(0L);
        register(branchId, emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("branchId", branchId)));
        } catch (IOException exception) {
            remove(branchId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    void register(Integer branchId, SseEmitter emitter) {
        emittersByBranch.computeIfAbsent(branchId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(branchId, emitter));
        emitter.onTimeout(() -> {
            remove(branchId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(branchId, emitter));
    }

    public void queueChanged(Integer branchId) {
        Set<SseEmitter> emitters = emittersByBranch.get(branchId);
        if (emitters == null) return;
        emitters.forEach(emitter -> send(branchId, emitter, SseEmitter.event().name("queue-updated")
            .id(Instant.now().toString()).data(Map.of("branchId", branchId))));
    }

    @Scheduled(fixedDelay = 25_000)
    public void keepAlive() {
        emittersByBranch.forEach((branchId, emitters) ->
            emitters.forEach(emitter -> send(branchId, emitter, SseEmitter.event().comment("keepalive"))));
    }

    private void send(Integer branchId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(branchId, emitter);
            emitter.completeWithError(exception);
            log.debug("Removed closed kitchen event stream for branch {}", branchId);
        }
    }

    private void remove(Integer branchId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByBranch.get(branchId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) emittersByBranch.remove(branchId, emitters);
        }
    }
}
