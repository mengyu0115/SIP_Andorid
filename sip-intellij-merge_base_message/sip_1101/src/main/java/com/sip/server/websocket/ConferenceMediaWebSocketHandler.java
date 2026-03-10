package com.sip.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sip.common.dto.MediaFrameMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议媒体WebSocket处理器
 * 用于处理音视频/屏幕共享的实时传输
 *
 * @author SIP Team
 * @since 2025-12-09
 */
@Component
public class ConferenceMediaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceMediaWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ConferenceMediaRelayService mediaRelayService;

    // 存储所有活跃的WebSocket连接: sessionId -> session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储用户ID到会话ID的映射: userId -> sessionId
    private final Map<Long, String> userSessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("✅ 媒体WebSocket连接建立: sessionId={}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            // 解析消息
            MediaFrameMessage frameMessage = objectMapper.readValue(payload, MediaFrameMessage.class);
            String messageType = frameMessage.getType();

            logger.debug("📨 收到媒体消息: type={}, conferenceId={}, userId={}",
                messageType, frameMessage.getConferenceId(), frameMessage.getUserId());

            // 处理不同类型的消息
            switch (messageType) {
                case "JOIN_CONFERENCE":
                    handleJoinConference(session, frameMessage);
                    break;

                case "LEAVE_CONFERENCE":
                    handleLeaveConference(frameMessage);
                    break;

                case "MEDIA_FRAME":
                    handleMediaFrame(frameMessage);
                    break;

                default:
                    logger.warn("⚠️ 未知的消息类型: {}", messageType);
            }

        } catch (Exception e) {
            logger.error("❌ 处理媒体消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理加入会议
     */
    private void handleJoinConference(WebSocketSession session, MediaFrameMessage message) {
        Long conferenceId = message.getConferenceId();
        Long userId = message.getUserId();
        String sessionId = session.getId();

        if (conferenceId == null || userId == null) {
            logger.warn("⚠️ 无效的加入会议消息: {}", message);
            return;
        }

        // 存储用户ID到会话ID的映射
        userSessionMap.put(userId, sessionId);

        // 添加到媒体转发服务
        if (mediaRelayService != null) {
            mediaRelayService.addParticipant(conferenceId, userId, sessionId);
            logger.info("✅ 用户加入媒体会议: userId={}, conferenceId={}, sessionId={}",
                userId, conferenceId, sessionId);
        }
    }

    /**
     * 处理离开会议
     */
    private void handleLeaveConference(MediaFrameMessage message) {
        Long conferenceId = message.getConferenceId();
        Long userId = message.getUserId();

        if (conferenceId == null || userId == null) {
            logger.warn("⚠️ 无效的离开会议消息: {}", message);
            return;
        }

        // 从媒体转发服务移除
        if (mediaRelayService != null) {
            mediaRelayService.removeParticipant(conferenceId, userId);
            logger.info("✅ 用户离开媒体会议: userId={}, conferenceId={}", userId, conferenceId);
        }

        // 清理映射
        userSessionMap.remove(userId);
    }

    /**
     * 处理媒体帧
     */
    private void handleMediaFrame(MediaFrameMessage message) {
        if (mediaRelayService == null) {
            logger.warn("⚠️ 媒体转发服务未启用");
            return;
        }

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
        try {
            // 获取需要接收此帧的参与者
            Long senderId = message.getUserId();
            Long conferenceId = message.getConferenceId();

            for (Long participantId : mediaRelayService.getParticipants(conferenceId)) {
                if (!participantId.equals(senderId)) {
                    // 发送给其他参与者
                    sendMediaFrameToUser(participantId, message);
                }
            }

        } catch (Exception e) {
            logger.error("❌ 转发媒体帧失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送媒体帧给指定用户
     */
    private void sendMediaFrameToUser(Long userId, MediaFrameMessage frame) {
        try {
            String sessionId = userSessionMap.get(userId);
            if (sessionId == null) {
                logger.warn("⚠️ 用户会话未找到: userId={}", userId);
                return;
            }

            WebSocketSession session = sessions.get(sessionId);
            if (session == null || !session.isOpen()) {
                logger.warn("⚠️ 会话不存在或已关闭: sessionId={}", sessionId);
                return;
            }

            // 转换为JSON并发送
            String json = objectMapper.writeValueAsString(frame);
            session.sendMessage(new TextMessage(json));

        } catch (Exception e) {
            logger.error("❌ 发送媒体帧失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        // 清理用户映射
        userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));

        logger.info("❌ 媒体WebSocket连接关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("⚠️ 媒体WebSocket传输错误: sessionId={}", session.getId(), exception);
        session.close();
    }
}
