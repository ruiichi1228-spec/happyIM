package com.happyim.api.config;

import com.happyim.common.mapper.AdminUserMapper;
import com.happyim.common.model.entity.AdminUser;
import com.happyim.common.security.AdminRequired;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;

    public AdminAuthInterceptor(JwtUtil jwtUtil, AdminUserMapper adminUserMapper) {
        this.jwtUtil = jwtUtil;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AdminRequired annotation = handlerMethod.getMethodAnnotation(AdminRequired.class);
        if (annotation == null) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        String token = header.substring(7);
        Long adminId;
        try {
            adminId = jwtUtil.getAdminId(token);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOT_LOGIN);
        }

        AdminUser admin = adminUserMapper.findById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "管理员账号不存在或已被删除");
        }

        request.setAttribute("adminId", adminId);
        return true;
    }
}
