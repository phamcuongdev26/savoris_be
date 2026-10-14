package com.savoris.repository;

import com.savoris.entity.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<RecipeEntity, Integer> {
    List<RecipeEntity> findByMenuItemId(Integer menuItemId);
    List<RecipeEntity> findByMenuItemIdIn(Collection<Integer> menuItemIds);
    void deleteByMenuItemId(Integer menuItemId);
}
