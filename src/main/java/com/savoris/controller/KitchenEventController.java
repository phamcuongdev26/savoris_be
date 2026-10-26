package com.savoris.controller;

import com.savoris.security.BranchAccessGuard;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.service.impl.KitchenEventStreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/kitchen")
@RequiredArgsConstructor
@Tag(name = "Kitchen", description = "Kitchen display (KDS) and peak-hour load")
public class KitchenEventController {

    private final KitchenEventStreamRegistry streamRegistry;
    private final BranchAccessGuard branchAccessGuard;

    @GetMapping(path = "/branches/{branchId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Luồng cập nhật hàng đợi bếp", description = "Gửi sự kiện queue-updated khi hàng đợi của chi nhánh đổi")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize(Roles.KITCHEN)
    public SseEmitter stream(@CurrentUser Integer staffId, @PathVariable Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return streamRegistry.subscribe(branchId);
    }
}
