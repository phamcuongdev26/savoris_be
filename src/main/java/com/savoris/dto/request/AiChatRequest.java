package com.savoris.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatRequest(
    @NotBlank @Size(max = 1500) String message,
    @Size(max = 10) List<@jakarta.validation.Valid AiChatTurn> history,
    Boolean agentMode
) {
    public AiChatRequest(String message, List<AiChatTurn> history) {
        this(message, history, false);
    }

    public record AiChatTurn(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 1500) String content
    ) {}
}
