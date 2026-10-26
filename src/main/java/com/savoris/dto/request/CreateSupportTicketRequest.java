package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
    @NotBlank @Size(max = 100) String customerName,
    @NotBlank @Pattern(regexp = "[+0-9() .-]{8,20}") String customerPhone,
    Integer branchId,
    @NotBlank @Size(max = 2000) String issue,
    @Size(max = 4000) String conversationContext
) {}
