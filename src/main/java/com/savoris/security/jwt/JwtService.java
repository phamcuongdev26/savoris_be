package com.savoris.security.jwt;

import com.savoris.entity.StaffEntity;

public interface JwtService {

    String generateAccessToken(StaffEntity staff);

    String generateRefreshToken(StaffEntity staff);

    Integer validateAccessToken(String token);

    Integer validateRefreshToken(String token);

    Integer extractStaffId(String token);

    boolean isTokenExpired(String token);

    long getAccessTokenExpirationSeconds();
}
