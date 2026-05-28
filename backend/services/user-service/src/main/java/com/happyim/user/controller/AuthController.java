package com.happyim.user.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.model.dto.*;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendCode(req.getEmail(), req.getType());
        return ApiResponse.message("验证码已发送");
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResult>> register(@Valid @RequestBody RegisterRequest req) {
        RegisterResult result = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("注册成功，请登录", result));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String ip = getClientIp(request);
        TokenResponse tokens = authService.login(req, ip);
        return ApiResponse.success("登录成功", tokens);
    }

    @PostMapping("/refresh-token")
    public ApiResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) {
        TokenResponse tokens = authService.refreshToken(req.getRefreshToken());
        return ApiResponse.success("Token刷新成功", tokens);
    }

    @PostMapping("/logout")
    @LoginRequired
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest req, HttpServletRequest request) {
        String accessToken = extractToken(request);
        authService.logout(accessToken, req.getRefreshToken());
        return ApiResponse.message("登出成功");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.message("密码重置成功，请重新登录");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }
        return header.substring(7);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
