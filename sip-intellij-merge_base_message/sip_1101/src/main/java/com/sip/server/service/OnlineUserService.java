package com.sip.server.service;

import com.sip.server.entity.User;
import com.sip.server.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 在线用户管理服务
 *
 * 功能：
 * - 管理用户登录会话
 * - 查看在线用户列表
 * - 用户登录/登出
 *
 * @author SIP Team
 * @version 1.0
 */
@Service
public class OnlineUserService {

    private static final Logger logger = LoggerFactory.getLogger(OnlineUserService.class);

    @Autowired
    private UserMapper userMapper;

    /**
     * 在线用户会话信息
     */
    public static class OnlineSession {
        private Long userId;
        private String username;
        private String sipUri;
        private String ipAddress;
        private LocalDateTime loginTime;
        private LocalDateTime lastActiveTime;
        private String deviceInfo;

        public OnlineSession(Long userId, String username, String sipUri, String ipAddress) {
            this.userId = userId;
            this.username = username;
            this.sipUri = sipUri;
            this.ipAddress = ipAddress;
            this.loginTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
        }

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getSipUri() { return sipUri; }
        public void setSipUri(String sipUri) { this.sipUri = sipUri; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public LocalDateTime getLoginTime() { return loginTime; }
        public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }

        public LocalDateTime getLastActiveTime() { return lastActiveTime; }
        public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }

        public String getDeviceInfo() { return deviceInfo; }
        public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    }

    // 在线用户Map (userId -> OnlineSession)
    private final Map<Long, OnlineSession> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 用户登录
     */
    public boolean userLogin(Long userId, String ipAddress, String deviceInfo) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                logger.warn("用户登录失败 - 用户不存在: {}", userId);
                return false;
            }

            // 构造 SIP URI: sip:sipId@sipDomain
            String sipUri = user.getSipUri();

            OnlineSession session = new OnlineSession(
                userId,
                user.getUsername(),
                sipUri,
                ipAddress
            );
            session.setDeviceInfo(deviceInfo);

            onlineUsers.put(userId, session);
            logger.info("用户登录成功: {} ({}), IP: {}", user.getUsername(), userId, ipAddress);

            return true;
        } catch (Exception e) {
            logger.error("用户登录异常: {}", userId, e);
            return false;
        }
    }

    /**
     * 用户登出
     */
    public void userLogout(Long userId) {
        OnlineSession session = onlineUsers.remove(userId);
        if (session != null) {
            logger.info("用户登出: {} ({})", session.getUsername(), userId);
        }
    }

    /**
     * 更新用户活跃时间
     */
    public void updateUserActivity(Long userId) {
        OnlineSession session = onlineUsers.get(userId);
        if (session != null) {
            session.setLastActiveTime(LocalDateTime.now());
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    /**
     * 获取所有在线用户
     */
    public List<OnlineSession> getAllOnlineUsers() {
        return new ArrayList<>(onlineUsers.values());
    }

    /**
     * 根据用户名搜索在线用户
     */
    public List<OnlineSession> searchOnlineUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllOnlineUsers();
        }

        String lowerKeyword = keyword.toLowerCase();
        return onlineUsers.values().stream()
            .filter(session ->
                session.getUsername().toLowerCase().contains(lowerKeyword) ||
                session.getSipUri().toLowerCase().contains(lowerKeyword)
            )
            .collect(Collectors.toList());
    }

    /**
     * 获取用户会话信息
     */
    public OnlineSession getUserSession(Long userId) {
        return onlineUsers.get(userId);
    }

    /**
     * 清理超时会话（超过30分钟无活动）
     */
    public void cleanupInactiveSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<Long> inactiveUsers = new ArrayList<>();

        onlineUsers.forEach((userId, session) -> {
            if (session.getLastActiveTime().isBefore(cutoffTime)) {
                inactiveUsers.add(userId);
            }
        });

        inactiveUsers.forEach(userId -> {
            OnlineSession session = onlineUsers.remove(userId);
            logger.info("清理超时会话: {} ({})", session.getUsername(), userId);
        });

        if (!inactiveUsers.isEmpty()) {
            logger.info("已清理 {} 个超时会话", inactiveUsers.size());
        }
    }

    /**
     * 强制用户下线（管理员操作）
     */
    public boolean forceLogout(Long userId) {
        OnlineSession session = onlineUsers.remove(userId);
        if (session != null) {
            logger.warn("管理员强制用户下线: {} ({})", session.getUsername(), userId);
            return true;
        }
        return false;
    }
}
