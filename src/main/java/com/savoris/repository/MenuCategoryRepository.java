package com.savoris.repository;

import com.savoris.entity.MenuCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategoryEntity, Integer> {
    List<MenuCategoryEntity> findByNameContainingIgnoreCase(String name);
    List<MenuCategoryEntity> findByIsActiveTrueOrderBySortOrderAscNameAsc();
}
