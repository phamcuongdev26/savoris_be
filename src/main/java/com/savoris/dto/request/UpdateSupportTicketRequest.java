package com.savoris.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketRequest(@NotNull @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED") String status) {}
