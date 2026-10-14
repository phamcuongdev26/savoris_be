package com.savoris.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.entity.SiteConfigEntity;
import com.savoris.repository.SiteConfigRepository;
import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
@Tag(name = "Site CMS", description = "Nội dung trang web công khai")
public class SiteController {

    private final SiteConfigRepository repo;
    private final ObjectMapper objectMapper;

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Object> getConfig(String key) {
        Optional<SiteConfigEntity> opt = repo.findById(key);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        try {
            Object parsed = objectMapper.readValue(opt.get().getContent(), Object.class);
            return ResponseEntity.ok(parsed);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse stored config: " + key);
        }
    }

    private ResponseEntity<Object> putConfig(String key, Object body, Integer staffId) {
        try {
            String json = objectMapper.writeValueAsString(body);
            SiteConfigEntity entity = SiteConfigEntity.builder()
                    .configKey(key)
                    .content(json)
                    .updatedBy(staffId)
                    .build();
            repo.save(entity);
            return ResponseEntity.ok(body);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize config: " + key);
        }
    }

    // ── GET /site/all ─────────────────────────────────────────────────────────

    @GetMapping("/all")
    @Operation(summary = "Tất cả cấu hình trang web", description = "Trả về map tất cả config một lần (tối ưu tải trang)")
    public ResponseEntity<Map<String, Object>> getAll() {
        List<SiteConfigEntity> all = repo.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        for (SiteConfigEntity entity : all) {
            try {
                Object parsed = objectMapper.readValue(entity.getContent(), Object.class);
                result.put(entity.getConfigKey(), parsed);
            } catch (JsonProcessingException e) {
                result.put(entity.getConfigKey(), null);
            }
        }
        return ResponseEntity.ok(result);
    }

    // ── hero ──────────────────────────────────────────────────────────────────

    @GetMapping("/hero")
    @Operation(summary = "Lấy cấu hình hero section")
    public ResponseEntity<Object> getHero() {
        return getConfig("hero");
    }

    @PutMapping("/hero")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình hero section")
    public ResponseEntity<Object> putHero(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("hero", body, staffId);
    }

    // ── sections ──────────────────────────────────────────────────────────────

    @GetMapping("/sections")
    @Operation(summary = "Lấy cấu hình sections")
    public ResponseEntity<Object> getSections() {
        return getConfig("sections");
    }

    @PutMapping("/sections")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình sections")
    public ResponseEntity<Object> putSections(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("sections", body, staffId);
    }

    // ── milestones ────────────────────────────────────────────────────────────

    @GetMapping("/milestones")
    @Operation(summary = "Lấy cấu hình milestones")
    public ResponseEntity<Object> getMilestones() {
        return getConfig("milestones");
    }

    @PutMapping("/milestones")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình milestones")
    public ResponseEntity<Object> putMilestones(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("milestones", body, staffId);
    }

    // ── gallery ───────────────────────────────────────────────────────────────

    @GetMapping("/gallery")
    @Operation(summary = "Lấy cấu hình gallery")
    public ResponseEntity<Object> getGallery() {
        return getConfig("gallery");
    }

    @PutMapping("/gallery")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình gallery")
    public ResponseEntity<Object> putGallery(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("gallery", body, staffId);
    }

    // ── founder ───────────────────────────────────────────────────────────────

    @GetMapping("/founder")
    @Operation(summary = "Lấy cấu hình founder")
    public ResponseEntity<Object> getFounder() {
        return getConfig("founder");
    }

    @PutMapping("/founder")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình founder")
    public ResponseEntity<Object> putFounder(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("founder", body, staffId);
    }

    // ── order-config ──────────────────────────────────────────────────────────

    @GetMapping("/order-config")
    @Operation(summary = "Lấy cấu hình order-config")
    public ResponseEntity<Object> getOrderConfig() {
        return getConfig("order-config");
    }

    @PutMapping("/order-config")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình order-config")
    public ResponseEntity<Object> putOrderConfig(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("order-config", body, staffId);
    }

    // ── footer ────────────────────────────────────────────────────────────────

    @GetMapping("/footer")
    @Operation(summary = "Lấy cấu hình footer")
    public ResponseEntity<Object> getFooter() {
        return getConfig("footer");
    }

    @PutMapping("/footer")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình footer")
    public ResponseEntity<Object> putFooter(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("footer", body, staffId);
    }

    // ── nav ───────────────────────────────────────────────────────────────────

    @GetMapping("/nav")
    @Operation(summary = "Lấy cấu hình nav")
    public ResponseEntity<Object> getNav() {
        return getConfig("nav");
    }

    @PutMapping("/nav")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình nav")
    public ResponseEntity<Object> putNav(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("nav", body, staffId);
    }

    // ── manifesto ─────────────────────────────────────────────────────────────

    @GetMapping("/manifesto")
    @Operation(summary = "Lấy cấu hình manifesto")
    public ResponseEntity<Object> getManifesto() {
        return getConfig("manifesto");
    }

    @PutMapping("/manifesto")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật cấu hình manifesto")
    public ResponseEntity<Object> putManifesto(@RequestBody Object body, @CurrentUser Integer staffId) {
        return putConfig("manifesto", body, staffId);
    }
}
