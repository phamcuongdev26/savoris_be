package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportMessageRequest(@NotBlank @Size(max = 2000) String body) {}
