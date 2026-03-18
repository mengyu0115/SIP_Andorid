package com.sip.server.websocket;

import com.sip.common.dto.MediaFrameMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket消息处理器 - 处理会议媒体流
 *
 * @author SIP Team
 * @since 2025-12-09
 */
@Controller
public class ConferenceMediaWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceMediaWebSocketController.class);

    @Autowired(required = false)
    private ConferenceMediaRelayService mediaRelayService;

    /**
     * 处理媒体帧消息
     * 客户端发送到: /app/conference/media
     */
    @MessageMapping("/conference/media")
    public void handleMediaFrame(@Payload MediaFrameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        if (mediaRelayService == null) {
            logger.warn("⚠️ 媒体转发服务未启用");
            return;
        }

        try {
            // 验证消息
            if (message.getConferenceId() == null || message.getUserId() == null) {
                logger.warn("⚠️ 无效的媒体帧消息: {}", message);
                return;
            }

            // 检查用户是否在会议中
            if (!mediaRelayService.isUserInConference(message.getConferenceId(), message.getUserId())) {
                logger.warn("⚠️ 用户不在会议中: userId={}, conferenceId={}",
                    message.getUserId(), message.getConferenceId());
                return;
            }

            // 转发媒体帧
            mediaRelayService.relayMediaFrame(message);

        } catch (Exception e) {
            logger.error("❌ 处理媒体帧失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理参与者加入会议
     * 客户端发送到: /app/conference/join
     */
    @MessageMapping("/conference/join")
    public void handleJoinConference(@Payload Map<String, Object> message, SimpMessageHeaderAccessor headerAccessor) {
        if (mediaRelayService == null) {
            logger.warn("⚠️ 媒体转发服务未启用");
            return;
        }

        try {
            Long conferenceId = getLong(message, "conferenceId");
            Long userId = getLong(message, "userId");
            String sessionId = headerAccessor.getSessionId();

            if (conferenceId == null || userId == null) {
                logger.warn("⚠️ 无效的加入会议消息: {}", message);
                return;
            }

            mediaRelayService.addParticipant(conferenceId, userId, sessionId);

            logger.info("✅ 用户加入媒体会议: userId={}, conferenceId={}, sessionId={}",
                userId, conferenceId, sessionId);

        } catch (Exception e) {
            logger.error("❌ 处理加入会议失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理参与者离开会议
     * 客户端发送到: /app/conference/leave
     */
    @MessageMapping("/conference/leave")
    public void handleLeaveConference(@Payload Map<String, Object> message) {
        if (mediaRelayService == null) {
            logger.warn("⚠️ 媒体转发服务未启用");
            return;
        }

        try {
            Long conferenceId = getLong(message, "conferenceId");
            Long userId = getLong(message, "userId");

            if (conferenceId == null || userId == null) {
                logger.warn("⚠️ 无效的离开会议消息: {}", message);
                return;
            }

            mediaRelayService.removeParticipant(conferenceId, userId);

            logger.info("✅ 用户离开媒体会议: userId={}, conferenceId={}", userId, conferenceId);

        } catch (Exception e) {
            logger.error("❌ 处理离开会议失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取会议统计信息
     * 客户端发送到: /app/conference/stats
     */
    @MessageMapping("/conference/stats")
    public Map<String, Object> getStatistics() {
        if (mediaRelayService == null) {
            return Map.of("error", "媒体转发服务未启用");
        }
        return mediaRelayService.getStatistics();
    }

    /**
     * 辅助方法：从Map中获取Long值
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            logger.warn("⚠️ 无法解析Long值: key={}, value={}", key, value);
            return null;
        }
    }
}
