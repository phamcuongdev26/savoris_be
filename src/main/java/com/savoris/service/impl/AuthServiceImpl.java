package com.savoris.service.impl;

import com.savoris.constant.StaffStatus;
import com.savoris.dto.request.ChangePasswordRequest;
import com.savoris.dto.request.LoginRequest;
import com.savoris.dto.request.RefreshTokenRequest;
import com.savoris.dto.response.AuthResponse;
import com.savoris.dto.response.StaffResponse;
import com.savoris.entity.RefreshTokenEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.RefreshTokenRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.jwt.JwtService;
import com.savoris.security.LoginAttemptGuard;
import com.savoris.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final StaffRepository staffRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String clientAddress) {
        loginAttemptGuard.checkAllowed(request.getUsername(), clientAddress);

        // 1. Find staff by username (an unknown username counts as a failed attempt too)
        StaffEntity staff = staffRepository.findByUsername(request.getUsername()).orElse(null);
        if (staff == null) {
            loginAttemptGuard.recordFailure(request.getUsername(), clientAddress);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 2. Check if staff is resigned
        if (staff.getStatus() == StaffStatus.RESIGNED) {
            throw new AppException(ErrorCode.ACCOUNT_RESIGNED);
        }

        // 3. Verify password
        if (!passwordEncoder.matches(request.getPassword(), staff.getPasswordHash())) {
            loginAttemptGuard.recordFailure(request.getUsername(), clientAddress);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptGuard.recordSuccess(request.getUsername(), clientAddress);

        // 4. Generate tokens
        String accessToken = jwtService.generateAccessToken(staff);
        String refreshToken = jwtService.generateRefreshToken(staff);

        // 5. Save refresh token in database
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusSeconds(7 * 24 * 60 * 60); // 7 days

        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
            .staffId(staff.getId())
            .tokenHash(hashToken(refreshToken))
            .expiresAt(expiresAt)
            .createdAt(LocalDateTime.now())
            .build();

        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpirationSeconds(),
            StaffResponse.from(staff)
        );
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        // 1. Validate refresh token
        Integer staffId = jwtService.validateRefreshToken(request.getRefreshToken());
        if (staffId == null) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }

        // 2. Check if token is revoked in database
        RefreshTokenEntity token = refreshTokenRepository
            .findByTokenHashAndRevokedAtIsNull(hashToken(request.getRefreshToken()))
            .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_REVOKED));

        // 3. Get staff
        StaffEntity staff = staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        if (staff.getStatus() == StaffStatus.RESIGNED) {
            throw new AppException(ErrorCode.ACCOUNT_RESIGNED);
        }

        // 4. Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(staff);
        String newRefreshToken = jwtService.generateRefreshToken(staff);

        // 5. Save new refresh token
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusSeconds(7 * 24 * 60 * 60);

        RefreshTokenEntity newToken = RefreshTokenEntity.builder()
            .staffId(staff.getId())
            .tokenHash(hashToken(newRefreshToken))
            .expiresAt(expiresAt)
            .createdAt(LocalDateTime.now())
            .build();

        refreshTokenRepository.save(newToken);

        // 6. Optionally revoke old token
        refreshTokenRepository.revokeByTokenHash(
            hashToken(request.getRefreshToken()),
            LocalDateTime.now()
        );

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(jwtService.getAccessTokenExpirationSeconds());
        response.setStaff(StaffResponse.from(staff));
        return response;
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.revokeByTokenHash(
            hashToken(refreshToken),
            LocalDateTime.now()
        );
    }

    @Override
    public StaffResponse getCurrentStaff(Integer staffId) {
        StaffEntity staff = staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));
        return StaffResponse.from(staff);
    }

    @Override
    @Transactional
    public void changePassword(Integer staffId, ChangePasswordRequest request) {
        StaffEntity staff = staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), staff.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Current password is incorrect");
        }

        // Update password
        staff.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        staffRepository.save(staff);

        // Revoke all tokens (force re-login)
        refreshTokenRepository.revokeAllTokensByStaffId(staffId, LocalDateTime.now());
    }

    // Store only a SHA-256 hash: a leaked table cannot be replayed, and it fits the column (RS256 tokens are > 255 chars)
    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
