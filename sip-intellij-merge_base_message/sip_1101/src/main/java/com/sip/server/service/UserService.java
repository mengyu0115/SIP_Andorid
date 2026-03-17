package com.sip.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sip.server.entity.User;
import com.sip.server.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务
 *
 * 实现用户的注册、登录、信息管理等功能
 *
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // 使用延迟注入解决循环依赖问题
    @Autowired(required = false)
    private org.springframework.context.ApplicationContext applicationContext;

    private SimpMessagingTemplate messagingTemplate;

    // 假设 SipAccountPool 是存在的，如果不存在或者不需要了，你需要根据实际情况移除相关代码
    // 这里为了保持代码结构，我先保留了引用，但要注意你的项目结构中是否还有这个类
    // 如果 SipAccountPool 也不要了（毕竟是固定账号），那么 register 方法可能需要大改或者直接废弃
    // 既然你的需求是“固定账号”，register 方法其实已经不重要了，重点是 login。
    // 为了通过编译，我先把 SipAccountPool 相关的逻辑注释掉，或者保留引用但假设它存在。
    // 根据你之前的“固定账号”描述，register 其实是用不到的。

    /**
     * 用户注册 (已简化/废弃，仅保留方法签名防止编译错误，或者根据需要实现简单的直接插入)
     * 因为现在是固定账号模式，通常不需要动态注册。
     */
    public User register(String username, String password, String nickname, String avatar) {
        // 固定账号模式下，通常不在 APP 端注册。
        // 如果非要保留，这里需要大幅简化，且不能使用不存在的字段。
        log.info("用户注册尝试 (固定账号模式下不建议使用): username={}", username);
        throw new RuntimeException("系统维护中，暂停新用户注册");
    }

    /**
     * 用户登录 (Production Ready - 明文密码版本 + 登录互踢机制)
     *
     * 按照需求实现：
     * 1. 使用 QueryWrapper 查询真实 MySQL 数据库
     * 2. 使用明文密码对比
     * 3. 检查是否已有活跃登录（login_token不为空）
     * 4. 如果已登录，通过WebSocket通知旧设备下线
     * 5. 生成新token并更新数据库
     * 6. 返回完整的 SIP 连接信息
     *
     * @param username 用户名
     * @param password 密码
     * @param deviceInfo 设备信息（可选）
     * @return 登录成功的用户（包含完整 SIP 信息和新token）
     */
    public Map<String, Object> login(String username, String password, String deviceInfo) {
        log.info("========== 开始登录流程 ==========");
        log.info("用户登录: username={}, device={}", username, deviceInfo);
        log.info("收到密码: {}", password);

        // 1. 使用 QueryWrapper 查询数据库
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            log.error("登录失败: 用户不存在, username={}", username);
            throw new RuntimeException("用户不存在");
        }

        log.info("数据库查询成功: userId={}, username={}", user.getId(), user.getUsername());
        log.info("数据库密码: {}", user.getPassword());

        // 2. 明文密码验证
        if (!password.equals(user.getPassword())) {
            log.error("登录失败: 密码错误, 输入密码={}, 数据库密码={}", password, user.getPassword());
            throw new RuntimeException("密码错误");
        }

        log.info("✅ 密码验证成功");

        // 3. 检查是否已有活跃登录（登录互踢机制）
        String oldToken = user.getLoginToken();
        String oldDevice = user.getLoginDevice();

        if (oldToken != null && !oldToken.isEmpty()) {
            log.warn("⚠️ 检测到用户已在其他设备登录");
            log.warn("   旧设备: {}", oldDevice != null ? oldDevice : "未知");
            log.warn("   旧Token: {}", oldToken.substring(0, Math.min(20, oldToken.length())) + "...");

            // 通过WebSocket通知旧设备下线
            kickOutOldDevice(user.getId(), oldToken, oldDevice);
        }

        // 4. 生成新的登录token
        String newToken = generateToken(user.getId(), username);
        log.info("生成新Token: {}", newToken.substring(0, Math.min(20, newToken.length())) + "...");

        // 5. 更新用户登录状态
        user.setLoginToken(newToken);
        user.setLoginDevice(deviceInfo != null ? deviceInfo : "Unknown Device");
        user.setStatus(1); // 设置为在线
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("✅ 用户登录成功");
        log.info("========== 登录流程完成 ==========");

        // 6. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("token", newToken);
        result.put("kickedOut", oldToken != null && !oldToken.isEmpty());  // 是否踢出了旧设备

        return result;
    }

    /**
     * 踢出旧设备（通过WebSocket通知）
     */
    private void kickOutOldDevice(Long userId, String oldToken, String oldDevice) {
        try {
            // 延迟获取 messagingTemplate，避免循环依赖
            if (messagingTemplate == null && applicationContext != null) {
                try {
                    messagingTemplate = applicationContext.getBean(SimpMessagingTemplate.class);
                } catch (Exception e) {
                    log.debug("无法获取 SimpMessagingTemplate: {}", e.getMessage());
                }
            }

            if (messagingTemplate != null) {
                Map<String, Object> kickMessage = new HashMap<>();
                kickMessage.put("type", "FORCE_LOGOUT");
                kickMessage.put("reason", "您的账号在另一台设备登录");
                kickMessage.put("device", oldDevice);
                kickMessage.put("timestamp", System.currentTimeMillis());

                // 发送WebSocket消息到旧设备
                String destination = "/user/" + userId + "/queue/system";
                messagingTemplate.convertAndSend(destination, kickMessage);

                log.info("✅ 已通过WebSocket通知旧设备下线: userId={}, device={}", userId, oldDevice);
            } else {
                log.warn("⚠️ WebSocket未启用，无法通知旧设备下线");
            }
        } catch (Exception e) {
            log.error("通知旧设备下线失败", e);
            // 不抛出异常，允许新登录继续
        }
    }

    /**
     * 生成登录Token
     */
    private String generateToken(Long userId, String username) {
        // 简单的token生成：userId-username-timestamp-随机数
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = String.valueOf((int)(Math.random() * 100000));
        return userId + "-" + username + "-" + timestamp + "-" + random;
    }

    /**
     * 用户登录（兼容旧版本，无设备信息）
     */
    public User login(String username, String password) {
        Map<String, Object> result = login(username, password, null);
        return (User) result.get("user");
    }

    /**
     * 根据 ID 获取用户信息
     */
    public User getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
            user.setSipPassword(null);
        }
        return user;
    }

    /**
     * 更新用户信息
     */
    public void updateUser(User user) {
        // user.setUpdateTime(LocalDateTime.now()); // 已删除字段
        userMapper.updateById(user);
        log.info("用户信息更新成功: id={}", user.getId());
    }

    /**
     * 更新用户状态
     */
    public void updateStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        // user.setUpdateTime(LocalDateTime.now()); // 已删除字段
        userMapper.updateById(user);
        log.info("用户状态更新: id={}, status={}", userId, status);
    }

    /**
     * 修改密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码 (这里假设是明文对比，如果之前是加密的需要改回去)
        if (!oldPassword.equals(user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        // 更新密码
        user.setPassword(newPassword); // 明文存储
        // user.setUpdateTime(LocalDateTime.now()); // 已删除字段
        userMapper.updateById(user);

        log.info("密码修改成功: id={}", userId);
    }

    /**
     * 搜索用户
     */
    public java.util.List<User> searchUsers(String keyword) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("username", keyword)
                .or().like("nickname", keyword);

        java.util.List<User> users = userMapper.selectList(queryWrapper);

        // 移除密码信息
        users.forEach(user -> {
            user.setPassword(null);
            user.setSipPassword(null);
        });

        return users;
    }

    /**
     * 获取所有用户列表
     */
    public java.util.List<User> getAllUsers() {
        java.util.List<User> users = userMapper.selectList(null);

        // 移除密码信息
        users.forEach(user -> {
            user.setPassword(null);
            user.setSipPassword(null);
        });

        return users;
    }
}