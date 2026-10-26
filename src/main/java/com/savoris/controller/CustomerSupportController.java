package com.savoris.controller;

import com.savoris.ai.AiChatRateLimiter;
import com.savoris.dto.request.CreateSupportTicketRequest;
import com.savoris.dto.request.UpdateSupportTicketRequest;
import com.savoris.dto.request.CreateSupportMessageRequest;
import com.savoris.dto.response.CreatedSupportTicketResponse;
import com.savoris.dto.response.SupportMessageResponse;
import com.savoris.dto.response.SupportTicketResponse;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.CustomerSupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CustomerSupportController {
    private final CustomerSupportService service;
    private final AiChatRateLimiter rateLimiter;

    @PostMapping("/public/support-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedSupportTicketResponse create(@Valid @RequestBody CreateSupportTicketRequest request) {
        rateLimiter.checkAllowed();
        return service.create(request);
    }

    @GetMapping("/public/support-tickets/{referenceCode}/messages")
    public List<SupportMessageResponse> customerMessages(@PathVariable String referenceCode,
        @RequestHeader("X-Support-Token") String accessToken) {
        return service.customerMessages(referenceCode, accessToken);
    }

    @PostMapping("/public/support-tickets/{referenceCode}/messages")
    public List<SupportMessageResponse> customerMessage(@PathVariable String referenceCode,
        @RequestHeader("X-Support-Token") String accessToken, @Valid @RequestBody CreateSupportMessageRequest request) {
        rateLimiter.checkAllowed();
        return service.customerMessage(referenceCode, accessToken, request);
    }

    @GetMapping("/support-tickets")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public List<SupportTicketResponse> list(@CurrentUser Integer staffId) { return service.list(staffId); }

    @GetMapping("/support-tickets/{ticketId}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public List<SupportMessageResponse> staffMessages(@CurrentUser Integer staffId, @PathVariable long ticketId) {
        return service.staffMessages(staffId, ticketId);
    }

    @PostMapping("/support-tickets/{ticketId}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public List<SupportMessageResponse> staffMessage(@CurrentUser Integer staffId, @PathVariable long ticketId,
        @Valid @RequestBody CreateSupportMessageRequest request) {
        return service.staffMessage(staffId, ticketId, request);
    }

    @PatchMapping("/support-tickets/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public SupportTicketResponse update(@CurrentUser Integer staffId, @PathVariable long ticketId,
        @Valid @RequestBody UpdateSupportTicketRequest request) {
        return service.update(staffId, ticketId, request);
    }
}
