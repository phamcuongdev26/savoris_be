package com.savoris.ai;

import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiChatRateLimiter {
    private static final String ACQUIRE = """
        INSERT INTO ai_request_rate_window (id, window_started_at, request_count)
        VALUES (1, date_trunc('minute', statement_timestamp()), 1)
        ON CONFLICT (id) DO UPDATE
        SET window_started_at = EXCLUDED.window_started_at,
            request_count = CASE
                WHEN ai_request_rate_window.window_started_at = EXCLUDED.window_started_at
                THEN ai_request_rate_window.request_count + 1
                ELSE 1
            END
        WHERE ai_request_rate_window.window_started_at <= EXCLUDED.window_started_at
          AND (ai_request_rate_window.window_started_at <> EXCLUDED.window_started_at
               OR ai_request_rate_window.request_count < ?)
        RETURNING request_count
        """;

    private final JdbcTemplate jdbc;

    @Value("${savoris.ai.max-requests-per-minute:30}")
    private int maxRequestsPerMinute;

    public void checkAllowed() {
        var acquired = jdbc.query(ACQUIRE, (rs, rowNum) -> rs.getInt(1), Math.max(1, maxRequestsPerMinute));
        if (acquired.isEmpty()) throw new AppException(ErrorCode.AI_RATE_LIMITED);
    }
}
