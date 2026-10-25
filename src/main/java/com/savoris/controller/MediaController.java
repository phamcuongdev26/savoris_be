package com.savoris.controller;

import com.savoris.image.ImageFiles;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MediaController {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final JdbcTemplate jdbc;

    @Value("${savoris.media.storage-dir:./media}")
    private String storageDirectory;

    public record UploadedMedia(String url, String alt) {}

    @PostMapping(path = "/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<UploadedMedia> upload(@RequestParam("file") MultipartFile file, @RequestParam("alt") String alt) throws IOException {
        if (alt == null || alt.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng nhập mô tả ảnh");
        byte[] bytes = file.getBytes();
        String type = ImageFiles.checkComplete(bytes, MAX_BYTES);
        String suffix = switch (type) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
        String name = UUID.randomUUID() + suffix;
        Path directory = Path.of(storageDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadedMedia("/api/v1/media/" + name, alt.trim()));
    }

    @DeleteMapping("/admin/media/{name}")
    @PreAuthorize(Roles.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable String name) throws IOException {
        if (!name.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) throw new AppException(ErrorCode.INVALID_REQUEST, "Tên ảnh không hợp lệ");
        String url = "/api/v1/media/" + name;
        String like = "%" + url + "%";
        boolean used = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from branch_page where payload::text like ?) or exists(select 1 from gallery_image where image_url like ?) or exists(select 1 from site_setting where payload::text like ?)", Boolean.class, like, like, like));
        if (used) throw new AppException(ErrorCode.CONFLICT, "Ảnh đang được dùng trong nội dung website");
        Files.deleteIfExists(Path.of(storageDirectory).toAbsolutePath().normalize().resolve(name));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/media/{name:.+}")
    public ResponseEntity<Resource> get(@PathVariable String name) throws IOException {
        if (!name.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) return ResponseEntity.notFound().build();
        Path file = Path.of(storageDirectory).toAbsolutePath().normalize().resolve(name).normalize();
        if (!file.startsWith(Path.of(storageDirectory).toAbsolutePath().normalize()) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(file.toUri());
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(resource);
    }
}
