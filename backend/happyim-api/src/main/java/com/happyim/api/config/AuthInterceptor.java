package com.happyim.api.config;

import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.security.LoginRequired;
import com.happyim.common.security.JwtUtil;
import com.happyim.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public AuthInterceptor(JwtUtil jwtUtil, AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        LoginRequired annotation = handlerMethod.getMethodAnnotation(LoginRequired.class);
        if (annotation == null) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        String token = header.substring(7);

        // 解析 token
        String jti;
        try {
            jti = jwtUtil.getJti(token);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        // 检查黑名单
        if (authService.isBlacklisted(jti)) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        // 验证 token 有效性
        try {
            jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        return true;
    }
}
