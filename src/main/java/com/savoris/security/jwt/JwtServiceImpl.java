package com.savoris.security.jwt;

import com.savoris.entity.StaffEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtProperties jwtProperties;
    private final RsaKeyProvider rsaKeyProvider;

    // Built once and shared: JwtParser is immutable and thread-safe, while building one per request under load
    // occasionally failed with a null-message exception and rejected valid tokens (random 401s at peak)
    private JwtParser parser;

    @PostConstruct
    void initParser() {
        parser = Jwts.parser().verifyWith(rsaKeyProvider.getPublicKey()).build();
    }

    @Override
    public String generateAccessToken(StaffEntity staff) {
        return Jwts.builder()
            // Unique id: two logins in the same second must not produce identical tokens (refresh tokens are unique in DB)
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(staff.getId()))
            .claim("staffId", staff.getId())
            .claim("username", staff.getUsername())
            .claim("role", staff.getRole().name())
            .claim("tokenType", "ACCESS")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration() * 1000))
            .signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();
    }

    @Override
    public String generateRefreshToken(StaffEntity staff) {
        return Jwts.builder()
            // Unique id: two logins in the same second must not produce identical tokens (refresh tokens are unique in DB)
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(staff.getId()))
            .claim("staffId", staff.getId())
            .claim("tokenType", "REFRESH")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration() * 1000))
            .signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
            .compact();
    }

    @Override
    public Integer validateAccessToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            String tokenType = (String) claims.get("tokenType");
            if (!"ACCESS".equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                return null;
            }

            return ((Number) claims.get("staffId")).intValue();
        } catch (Exception ex) {
            log.debug("Invalid or expired access token: {}", ex.toString());
            return null;
        }
    }

    @Override
    public Integer validateRefreshToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            String tokenType = (String) claims.get("tokenType");
            if (!"REFRESH".equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                return null;
            }

            return ((Number) claims.get("staffId")).intValue();
        } catch (Exception ex) {
            log.debug("Invalid or expired refresh token: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public Integer extractStaffId(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            return ((Number) claims.get("staffId")).intValue();
        } catch (Exception ex) {
            log.debug("Failed to extract staffId from token: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();

            return claims.getExpiration().before(new Date());
        } catch (Exception ex) {
            return true;
        }
    }

    @Override
    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpiration();
    }
}
