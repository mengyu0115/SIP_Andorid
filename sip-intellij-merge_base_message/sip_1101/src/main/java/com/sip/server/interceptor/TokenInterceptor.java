package com.sip.server.interceptor;

import com.sip.server.util.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Token 拦截器
 *
 * 拦截需要认证的请求,验证 Token 有效性
 *
 */
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 获取 Authorization 头
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            log.warn("请求头中缺少 Token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"未授权,请先登录\"}");
            return false;
        }

        // 提取 Token
        String token = jwtTokenUtil.extractToken(authorizationHeader);

        if (token == null) {
            log.warn("Token 格式错误");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"Token 格式错误\"}");
            return false;
        }

        // 验证 Token
        if (!jwtTokenUtil.validateToken(token)) {
            log.warn("Token 验证失败或已过期");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\"}");
            return false;
        }

        // 将用户信息存入 request 属性
        Long userId = jwtTokenUtil.getUserIdFromToken(token);
        String username = jwtTokenUtil.getUsernameFromToken(token);

        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        log.debug("Token 验证成功: userId={}, username={}", userId, username);

        return true;
    }
}