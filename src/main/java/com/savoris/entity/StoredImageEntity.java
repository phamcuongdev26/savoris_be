package com.savoris.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_images")
@Getter
@Setter
@NoArgsConstructor
public class StoredImageEntity {
    @Id
    @Column(name = "image_key", length = 50)
    private String imageKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
