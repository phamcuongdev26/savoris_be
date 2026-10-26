package com.savoris.dto.response;

import com.savoris.entity.CustomerSupportTicketEntity;
import java.time.LocalDateTime;

public record SupportTicketResponse(Long id, String referenceCode, String customerName, String customerPhone,
    Integer branchId, String issue, String conversationContext, String status, Integer handledBy,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SupportTicketResponse from(CustomerSupportTicketEntity e) {
        return new SupportTicketResponse(e.getId(), e.getReferenceCode(), e.getCustomerName(), e.getCustomerPhone(),
            e.getBranchId(), e.getIssue(), e.getConversationContext(), e.getStatus(), e.getHandledBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
