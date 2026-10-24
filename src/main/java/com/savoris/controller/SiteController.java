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

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
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
    private final JdbcTemplate jdbc;

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
        // Pull chain-level hero fields from the new CMS into the homepage config.
        Map<String, String> cmsSettings = jdbc.query(
            "SELECT payload->>'heroImage' AS hero_image, payload->>'brandLine' AS brand_line FROM site_setting WHERE id=1",
            rs -> {
                Map<String, String> values = new LinkedHashMap<>();
                if (rs.next()) {
                    values.put("heroImage", rs.getString("hero_image"));
                    values.put("brandLine", rs.getString("brand_line"));
                }
                return values;
            });
        String cmsHeroImage = cmsSettings.get("heroImage");
        String cmsBrandLine = cmsSettings.get("brandLine");
        ResponseEntity<Object> base = getConfig("hero");
        if ((cmsHeroImage == null || cmsHeroImage.isBlank())
                && (cmsBrandLine == null || cmsBrandLine.isBlank())) return base;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = base.getBody() instanceof Map<?,?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
            cfg.putIfAbsent("type", "image");
            cfg.putIfAbsent("overlay_opacity", 0.45);
            cfg.putIfAbsent("badge_enabled", false);
            cfg.putIfAbsent("badge_line1", ""); cfg.putIfAbsent("badge_line2", "");
            cfg.putIfAbsent("description", ""); cfg.putIfAbsent("cta_text", "ĐẶT BÀN"); cfg.putIfAbsent("cta_link", "#dat-ban");
            if (cmsBrandLine != null && !cmsBrandLine.isBlank()) cfg.put("description", cmsBrandLine);
            if (cmsHeroImage != null && !cmsHeroImage.isBlank()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> media = cfg.get("media") instanceof List<?> l
                    ? new ArrayList<>((List<Map<String, Object>>) l) : new ArrayList<>();
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("url", cmsHeroImage); img.put("public_id", cmsHeroImage); img.put("alt", "Hero");
                if (media.isEmpty()) media.add(img); else media.set(0, img);
                cfg.put("media", media);
            }
            return ResponseEntity.ok(cfg);
        } catch (Exception e) {
            return base;
        }
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
        // The legacy Website CMS is the chain-level source. Do not mix in branch galleries.
        ResponseEntity<Object> configured = getConfig("gallery");
        if (configured.getBody() != null) return configured;

        // New CMS entries for the whole chain have a NULL branch_id.
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, image_url, alt, sort_order FROM gallery_image WHERE branch_id IS NULL AND visible=true ORDER BY sort_order, id");
        if (!rows.isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.get("id"));
                item.put("image_url", r.get("image_url"));
                item.put("public_id", r.getOrDefault("image_url", ""));
                item.put("caption", r.getOrDefault("alt", ""));
                item.put("order", r.get("sort_order"));
                items.add(item);
            }
            return ResponseEntity.ok(items);
        }
        return ResponseEntity.ok(List.of());
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
