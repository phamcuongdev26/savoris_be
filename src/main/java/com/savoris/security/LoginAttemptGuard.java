package com.savoris.security;

import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops password guessing: after 5 wrong passwords for the same username from the same address within 15 minutes,
 * further attempts are refused for 15 minutes. Keyed by username + address so an attacker elsewhere cannot lock
 * a cashier out of their own till.
 * Kept in memory: enough for one server; a cluster would need a shared store (e.g. Redis).
 */
@Component
public class LoginAttemptGuard {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String username, String address) {
        Deque<Instant> recent = failures.get(key(username, address));
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            dropExpired(recent, Instant.now());
            if (recent.size() >= MAX_FAILURES) {
                long minutesLeft = Math.max(1, Duration.between(Instant.now(), recent.peekFirst().plus(WINDOW)).toMinutes());
                throw new AppException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many failed logins, try again in " + minutesLeft + " minutes");
            }
        }
    }

    public void recordFailure(String username, String address) {
        if (failures.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        Deque<Instant> recent = failures.computeIfAbsent(key(username, address), k -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addLast(Instant.now());
        }
    }

    public void recordSuccess(String username, String address) {
        failures.remove(key(username, address));
    }

    private static String key(String username, String address) {
        return (username == null ? "" : username.toLowerCase(Locale.ROOT)) + "|" + address;
    }

    private static void dropExpired(Deque<Instant> recent, Instant now) {
        while (!recent.isEmpty() && recent.peekFirst().plus(WINDOW).isBefore(now)) {
            recent.pollFirst();
        }
    }

    // Bounded memory even if someone sprays random usernames
    private void cleanup() {
        Instant now = Instant.now();
        failures.values().removeIf(recent -> {
            synchronized (recent) {
                dropExpired(recent, now);
                return recent.isEmpty();
            }
        });
    }
}
