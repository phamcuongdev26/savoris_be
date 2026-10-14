package com.savoris.repository;

import com.savoris.entity.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingRepository extends JpaRepository<SettingEntity, Integer> {
    Optional<SettingEntity> findByScopeKeyAndSettingKey(Integer scopeKey, String settingKey);
}
