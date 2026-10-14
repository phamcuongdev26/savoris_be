package com.savoris.repository;

import com.savoris.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Integer> {
    Optional<RefreshTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity rt SET rt.revokedAt = :now WHERE rt.staffId = :staffId")
    void revokeAllTokensByStaffId(Integer staffId, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity rt SET rt.revokedAt = :now WHERE rt.tokenHash = :tokenHash")
    void revokeByTokenHash(String tokenHash, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
