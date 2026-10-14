package com.savoris.repository;

import com.savoris.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<SupplierEntity, Integer> {
    List<SupplierEntity> findByIsActiveTrueOrderByName();
}
