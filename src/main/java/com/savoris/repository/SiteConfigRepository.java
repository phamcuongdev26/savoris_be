package com.savoris.repository;

import com.savoris.entity.SiteConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteConfigRepository extends JpaRepository<SiteConfigEntity, String> {
}
