package com.savoris.controller;

import com.savoris.image.Cloudinary;
import com.savoris.image.ImageFiles;
import com.savoris.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Photo upload for the menu: the owner picks a file, gets back its Cloudinary address and saves it on the dish.
 */
@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Tag(name = "Image", description = "Tải ảnh lên Cloudinary")
public class ImageController {

    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final Cloudinary cloudinary;

    public record UploadedImage(String url) {
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tải ảnh món lên", description = "Form-data field \"file\": PNG, JPEG hoặc WebP, tối đa 5MB. Trả về link ảnh trên Cloudinary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UploadedImage> upload(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        String url = cloudinary.upload(data, ImageFiles.check(data, MAX_BYTES), "savoris/menu");
        log.info("Menu photo uploaded to Cloudinary ({} bytes)", data.length);
        return ResponseEntity.ok(new UploadedImage(url));
    }

    @PostMapping(path = "/site", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.MANAGEMENT)
    @Operation(summary = "Tải ảnh trang web lên", description = "Form-data field \"file\": PNG, JPEG hoặc WebP, tối đa 5MB. Trả về link ảnh trên Cloudinary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UploadedImage> uploadSiteImage(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        String url = cloudinary.upload(data, ImageFiles.check(data, MAX_BYTES), "savoris/site");
        log.info("Site photo uploaded to Cloudinary ({} bytes)", data.length);
        return ResponseEntity.ok(new UploadedImage(url));
    }
}
