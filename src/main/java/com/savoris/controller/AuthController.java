package com.savoris.controller;

import com.savoris.dto.request.ChangePasswordRequest;
import com.savoris.dto.request.LoginRequest;
import com.savoris.dto.request.RefreshTokenRequest;
import com.savoris.dto.response.AuthResponse;
import com.savoris.dto.response.ErrorResponse;
import com.savoris.dto.response.StaffResponse;
import com.savoris.security.CurrentUser;
import com.savoris.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & Authorization endpoints")
public class AuthController {

    private final AuthService authService;

    @Value("${savoris.auth-cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${savoris.auth-cookie.same-site:None}")
    private String cookieSameSite;

    @PostMapping("/login")
    @Operation(
        summary = "Nhân viên đăng nhập",
        description = "Xác thực nhân viên bằng username và password, trả về access token và refresh token"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
        @ApiResponse(responseCode = "401", description = "Tài khoản hoặc mật khẩu không đúng",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Sai mật khẩu quá 5 lần trong 15 phút",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Nhân viên đã nghỉ việc",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StaffResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // Socket address, not X-Forwarded-For: a client could put anything in that header to dodge the limit
        AuthResponse response = authService.login(request, httpRequest.getRemoteAddr());
        return sessionResponse(response);
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Lấy access token mới",
        description = "Sử dụng refresh token để lấy access token mới (không cần đăng nhập lại)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token mới được cấp"),
        @ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ hoặc hết hạn",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StaffResponse> refresh(HttpServletRequest request) {
        String token = cookie(request, "savoris_refresh");
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AuthResponse response = authService.refresh(new RefreshTokenRequest(token));
        return sessionResponse(response);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Đăng xuất",
        description = "Thu hồi refresh token hiện tại (session sẽ bị hủy)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Đăng xuất thành công")
    })
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = cookie(request, "savoris_refresh");
        if (token != null) authService.logout(token);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clearCookie("savoris_access").toString())
            .header(HttpHeaders.SET_COOKIE, clearCookie("savoris_refresh").toString())
            .build();
    }

    @GetMapping("/me")
    @Operation(
        summary = "Thông tin nhân viên đang đăng nhập",
        description = "Lấy thông tin của nhân viên hiện tại từ JWT token"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thông tin nhân viên"),
        @ApiResponse(responseCode = "401", description = "Token không hợp lệ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StaffResponse> getCurrentStaff(@CurrentUser Integer staffId) {
        StaffResponse response = authService.getCurrentStaff(staffId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    @Operation(
        summary = "Đổi mật khẩu",
        description = "Đổi mật khẩu của nhân viên hiện tại"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Mật khẩu đã được đổi thành công"),
        @ApiResponse(responseCode = "400", description = "Mật khẩu hiện tại không đúng",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token không hợp lệ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> changePassword(
        @CurrentUser Integer staffId,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(staffId, request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<StaffResponse> sessionResponse(AuthResponse auth) {
        ResponseCookie access = ResponseCookie.from("savoris_access", auth.getAccessToken())
            .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/api/v1")
            .maxAge(auth.getExpiresIn()).build();
        ResponseCookie refresh = ResponseCookie.from("savoris_refresh", auth.getRefreshToken())
            .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite).path("/api/v1/auth")
            .maxAge(7 * 24 * 60 * 60).build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, access.toString())
            .header(HttpHeaders.SET_COOKIE, refresh.toString())
            .body(auth.getStaff());
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "").httpOnly(true).secure(cookieSecure)
            .sameSite(cookieSameSite).path(name.equals("savoris_refresh") ? "/api/v1/auth" : "/api/v1")
            .maxAge(0).build();
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
