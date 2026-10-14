package com.savoris.repository;

import com.savoris.entity.IngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<IngredientEntity, Integer> {
    Optional<IngredientEntity> findByCode(String code);
}
