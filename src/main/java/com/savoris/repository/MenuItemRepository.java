package com.savoris.repository;

import com.savoris.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Integer> {

    // Dish photos by id, for showing a picture next to every dish name
    default Map<Integer, String> imagesOf(Collection<Integer> ids) {
        Map<Integer, String> images = new HashMap<>();
        for (MenuItemEntity item : findAllById(ids)) {
            if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) images.put(item.getId(), item.getImageUrl());
        }
        return images;
    }
    Optional<MenuItemEntity> findByCode(String code);
    List<MenuItemEntity> findByCategoryId(Integer categoryId);
    List<MenuItemEntity> findByNameContainingIgnoreCase(String name);
    List<MenuItemEntity> findByIsActiveTrueAndDeletedAtIsNullOrderByCategoryIdAscNameAsc();
}
