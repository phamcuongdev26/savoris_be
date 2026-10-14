package com.savoris.repository;

import com.savoris.entity.StoredImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredImageRepository extends JpaRepository<StoredImageEntity, String> {
}
