package com.sip.server.controller;

import com.sip.common.result.Result;
import com.sip.server.service.OnlineUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户管理控制器
 *
 * 提供在线用户查询、管理功能
 *
 * @author SIP Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/online")
public class OnlineUserController {

    private static final Logger logger = LoggerFactory.getLogger(OnlineUserController.class);

    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * 获取所有在线用户
     * GET http://10.29.209.85:8081/api/online/users
     */
    @GetMapping("/users")
    public Result<List<OnlineUserService.OnlineSession>> getOnlineUsers() {
        try {
            List<OnlineUserService.OnlineSession> users = onlineUserService.getAllOnlineUsers();
            // ✅ 改为DEBUG级别，避免每3秒一次的重复日志
            logger.debug("查询在线用户列表，当前在线: {} 人", users.size());
            return Result.success(users);
        } catch (Exception e) {
            logger.error("查询在线用户失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 搜索在线用户
     * GET http://10.29.209.85:8081/api/online/search?keyword=user1
     */
    @GetMapping("/search")
    public Result<List<OnlineUserService.OnlineSession>> searchOnlineUsers(
            @RequestParam(required = false) String keyword) {
        try {
            List<OnlineUserService.OnlineSession> users = onlineUserService.searchOnlineUsers(keyword);
            // ✅ 改为DEBUG级别
            logger.debug("搜索在线用户，关键词: {}, 结果数: {}", keyword, users.size());
            return Result.success(users);
        } catch (Exception e) {
            logger.error("搜索在线用户失败", e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取在线用户统计
     * GET http://10.29.209.85:8081/api/online/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getOnlineStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalOnline", onlineUserService.getOnlineUserCount());
            stats.put("onlineUsers", onlineUserService.getAllOnlineUsers());

            // ✅ 改为DEBUG级别
            logger.debug("查询在线用户统计，当前在线: {}", stats.get("totalOnline"));
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("查询在线统计失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 检查用户是否在线
     * GET http://10.29.209.85:8081/api/online/check/{userId}
     */
    @GetMapping("/check/{userId}")
    public Result<Map<String, Object>> checkUserOnline(@PathVariable Long userId) {
        try {
            boolean isOnline = onlineUserService.isUserOnline(userId);
            OnlineUserService.OnlineSession session = onlineUserService.getUserSession(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("isOnline", isOnline);
            result.put("session", session);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("检查用户在线状态失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 用户登录（记录在线状态）
     * POST http://10.29.209.85:8081/api/online/login
     * Body: {"userId": 1, "deviceInfo": "Windows PC"}
     */
    @PostMapping("/login")
    public Result<String> userLogin(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String deviceInfo = (String) request.getOrDefault("deviceInfo", "Unknown");
            String ipAddress = getClientIpAddress(httpRequest);

            boolean success = onlineUserService.userLogin(userId, ipAddress, deviceInfo);

            if (success) {
                logger.info("用户登录成功: userId={}, IP={}", userId, ipAddress);
                return Result.success("登录成功");
            } else {
                return Result.error("登录失败");
            }
        } catch (Exception e) {
            logger.error("用户登录失败", e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }

    /**
     * 用户登出
     * POST http://10.29.209.85:8081/api/online/logout/{userId}
     */
    @PostMapping("/logout/{userId}")
    public Result<String> userLogout(@PathVariable Long userId) {
        try {
            onlineUserService.userLogout(userId);
            logger.info("用户登出: userId={}", userId);
            return Result.success("登出成功");
        } catch (Exception e) {
            logger.error("用户登出失败", e);
            return Result.error("登出失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户活跃时间
     * POST http://10.29.209.85:8081/api/online/heartbeat/{userId}
     */
    @PostMapping("/heartbeat/{userId}")
    public Result<String> userHeartbeat(@PathVariable Long userId) {
        try {
            onlineUserService.updateUserActivity(userId);
            return Result.success("心跳更新成功");
        } catch (Exception e) {
            logger.error("更新用户活跃时间失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 强制用户下线（管理员）
     * POST http://10.29.209.85:8081/api/online/force-logout/{userId}
     */
    @PostMapping("/force-logout/{userId}")
    public Result<String> forceLogout(@PathVariable Long userId) {
        try {
            boolean success = onlineUserService.forceLogout(userId);
            if (success) {
                logger.warn("管理员强制用户下线: userId={}", userId);
                return Result.success("强制下线成功");
            } else {
                return Result.error("用户不在线");
            }
        } catch (Exception e) {
            logger.error("强制下线失败", e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 清理超时会话
     * POST http://10.29.209.85:8081/api/online/cleanup
     */
    @PostMapping("/cleanup")
    public Result<String> cleanupInactiveSessions() {
        try {
            onlineUserService.cleanupInactiveSessions();
            return Result.success("清理完成");
        } catch (Exception e) {
            logger.error("清理超时会话失败", e);
            return Result.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况（取第一个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
