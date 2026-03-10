package com.sip.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sip.server.entity.UserPresence;
import com.sip.server.mapper.UserPresenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户在线状态服务
 * 使用数据库 + WebSocket 实现 Presence，绕过 SIP PUBLISH/SUBSCRIBE
 */
@Slf4j
@Service
public class UserPresenceService {

    @Autowired
    private UserPresenceMapper userPresenceMapper;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 发布用户状态
     *
     * @param sipId  SIP ID (如 "101")
     * @param status 状态: "online", "busy", "away", "offline"
     */
    public void publishStatus(String sipId, String status) {
        log.info("发布用户状态: sipId={}, status={}", sipId, status);

        // 1. 更新数据库
        LambdaUpdateWrapper<UserPresence> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserPresence::getSipId, sipId)
                .set(UserPresence::getStatus, status)
                .set(UserPresence::getLastUpdateTime, LocalDateTime.now());

        int updated = userPresenceMapper.update(null, updateWrapper);

        if (updated == 0) {
            log.warn("用户不存在，无法更新状态: sipId={}", sipId);
            return;
        }

        log.info("✅ 数据库状态已更新: sipId={}, status={}", sipId, status);

        // 2. 通过 WebSocket 广播状态变化（所有在线客户端都会收到）
        broadcastPresenceChange(sipId, status);
    }

    /**
     * 获取用户当前状态
     *
     * @param sipId SIP ID
     * @return 状态: "online", "busy", "away", "offline"
     */
    public String getStatus(String sipId) {
        UserPresence presence = userPresenceMapper.getBySipId(sipId);
        return presence != null ? presence.getStatus() : "offline";
    }

    /**
     * 获取多个用户的状态
     *
     * @param sipIds SIP ID 列表
     * @return Map<sipId, status>
     */
    public Map<String, String> getStatuses(List<String> sipIds) {
        List<UserPresence> presences = userPresenceMapper.getBySipIds(sipIds);

        Map<String, String> statusMap = new HashMap<>();
        for (UserPresence presence : presences) {
            statusMap.put(presence.getSipId(), presence.getStatus());
        }

        // 补充缺失的用户（默认 offline）
        for (String sipId : sipIds) {
            if (!statusMap.containsKey(sipId)) {
                statusMap.put(sipId, "offline");
            }
        }

        return statusMap;
    }

    /**
     * 获取所有在线用户
     *
     * @return 在线用户列表
     */
    public List<UserPresence> getOnlineUsers() {
        return userPresenceMapper.getOnlineUsers();
    }

    /**
     * 广播状态变化（通过 WebSocket）
     *
     * @param sipId  SIP ID
     * @param status 状态
     */
    private void broadcastPresenceChange(String sipId, String status) {
        if (messagingTemplate != null) {
            Map<String, String> message = new HashMap<>();
            message.put("sipId", sipId);
            message.put("status", status);
            message.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // 广播到 /topic/presence 主题
            messagingTemplate.convertAndSend("/topic/presence", message);
            log.info("✅ WebSocket 广播已发送: sipId={}, status={}", sipId, status);
        } else {
            log.warn("⚠️ WebSocket 未配置，无法广播状态变化");
        }
    }

    /**
     * 用户登录时调用 - 设置为在线
     *
     * @param sipId SIP ID
     */
    public void userOnline(String sipId) {
        publishStatus(sipId, "online");
    }

    /**
     * 用户退出时调用 - 设置为离线
     *
     * @param sipId SIP ID
     */
    public void userOffline(String sipId) {
        publishStatus(sipId, "offline");
    }
}
