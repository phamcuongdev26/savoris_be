package com.savoris.service;

import com.savoris.dto.request.ChangePasswordRequest;
import com.savoris.dto.request.LoginRequest;
import com.savoris.dto.request.RefreshTokenRequest;
import com.savoris.dto.response.AuthResponse;
import com.savoris.dto.response.StaffResponse;

public interface AuthService {
    /**
     * Staff login with username and password
     */
    AuthResponse login(LoginRequest request, String clientAddress);

    /**
     * Refresh access token using refresh token
     */
    AuthResponse refresh(RefreshTokenRequest request);

    /**
     * Logout (revoke refresh token)
     */
    void logout(String refreshToken);

    /**
     * Get current logged-in staff info
     */
    StaffResponse getCurrentStaff(Integer staffId);

    /**
     * Change password for current staff
     */
    void changePassword(Integer staffId, ChangePasswordRequest request);
}
