package com.savoris.dto.response;

import com.savoris.entity.CustomerSupportMessageEntity;
import java.time.LocalDateTime;

public record SupportMessageResponse(Long id, String senderType, String body, LocalDateTime createdAt) {
    public static SupportMessageResponse from(CustomerSupportMessageEntity entity) {
        return new SupportMessageResponse(entity.getId(), entity.getSenderType(), entity.getBody(), entity.getCreatedAt());
    }
}
