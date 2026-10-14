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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication & Authorization endpoints")
public class AuthController {

    private final AuthService authService;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // Socket address, not X-Forwarded-For: a client could put anything in that header to dodge the limit
        AuthResponse response = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
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
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Đăng xuất",
        description = "Thu hồi refresh token hiện tại (session sẽ bị hủy)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Đăng xuất thành công")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
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
}
