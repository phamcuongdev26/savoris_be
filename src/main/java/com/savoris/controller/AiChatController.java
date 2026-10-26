package com.savoris.controller;

import com.savoris.dto.request.AiChatRequest;
import com.savoris.dto.response.AiChatResponse;
import com.savoris.ai.AiChatRateLimiter;
import com.savoris.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Trợ lý khách hàng và dự báo vận hành")
public class AiChatController {
    private final AiChatService service;
    private final AiChatRateLimiter rateLimiter;

    @PostMapping("/chat")
    @Operation(summary = "Trò chuyện với trợ lý Savoris", description = "Chat public có giới hạn; dùng dữ liệu thực từ menu, chi nhánh và đặt bàn.")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        rateLimiter.checkAllowed();
        return ResponseEntity.ok(service.chat(request));
    }
}
