package com.sip.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sip.server.service.ConferenceServiceSimplified;
import com.sip.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议 WebSocket 处理器
 * 用于会议实时通信（参与者加入/离开、状态同步）
 *
 * @author SIP Team
 * @version 2.0
 */
@Component
public class ConferenceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ConferenceServiceSimplified conferenceService;

    @Autowired(required = false)
    private UserService userService;

    // 存储所有活跃的 WebSocket 连接: sessionId -> session
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储用户ID到session的映射: userId -> sessionId
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();

    // 存储会议ID到参与者的映射: conferenceId -> Set<userId>
    private final Map<Long, Set<Long>> conferenceParticipants = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("✅ WebSocket 连接建立: sessionId={}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.info("📨 收到 WebSocket 消息: sessionId={}, length={} bytes", session.getId(), payload.length());

        try {
            // 解析JSON消息
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");

            logger.info("📋 消息类型: {}", type);

            if (type == null) {
                logger.warn("⚠️ 消息类型为空");
                return;
            }

            switch (type) {
                case "join_conference":
                case "JOIN_CONFERENCE":
                    handleJoinConference(session, msg);
                    break;

                case "leave_conference":
                case "LEAVE_CONFERENCE":
                    handleLeaveConference(session, msg);
                    break;

                case "conference_message":
                    handleConferenceMessage(session, msg);
                    break;

                case "media_status_update":
                    handleMediaStatusUpdate(session, msg);
                    break;

                case "screen_share_start":
                    handleScreenShareStart(session, msg);
                    break;

                case "screen_share_stop":
                    handleScreenShareStop(session, msg);
                    break;

                default:
                    logger.warn("⚠️ 未知消息类型: {}", type);
            }

        } catch (Exception e) {
            logger.error("❌ 处理WebSocket消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理加入会议
     */
    private void handleJoinConference(WebSocketSession session, Map<String, Object> msg) {
        try {
            // 兼容两种格式: 扁平结构 或 data嵌套结构
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;

            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));
            String username = (String) data.get("username");

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long userId = Long.parseLong(userIdStr);

            logger.info("🚪 用户加入会议: conferenceId={}, userId={}, username={}", conferenceId, userId, username);

            // 存储用户会话映射
            userSessions.put(userId, session.getId());

            // 添加到会议参与者列表
            conferenceParticipants.computeIfAbsent(conferenceId, k -> ConcurrentHashMap.newKeySet()).add(userId);

            // 1. 发送已有参与者列表给新加入的用户
            sendExistingParticipants(session, conferenceId, userId);

            // 2. 广播新参与者加入消息给其他人
            broadcastParticipantJoined(conferenceId, userId, username);

            logger.info("✅ 用户成功加入会议: conferenceId={}, userId={}, 当前参与者数={}",
                       conferenceId, userId, conferenceParticipants.get(conferenceId).size());

        } catch (Exception e) {
            logger.error("❌ 处理加入会议失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送已有参与者列表给新用户
     */
    private void sendExistingParticipants(WebSocketSession session, Long conferenceId, Long newUserId) {
        try {
            Set<Long> participants = conferenceParticipants.get(conferenceId);
            if (participants == null || participants.isEmpty()) {
                logger.info("📋 会议暂无其他参与者: conferenceId={}", conferenceId);
                return;
            }

            // 构建参与者列表 (排除自己)
            StringBuilder participantsJson = new StringBuilder("[");
            boolean first = true;
            for (Long participantId : participants) {
                if (!participantId.equals(newUserId)) {
                    if (!first) participantsJson.append(",");

                    // 获取用户名
                    String participantName = "user" + participantId;
                    if (userService != null) {
                        try {
                            var user = userService.getUserById(participantId);
                            if (user != null && user.getNickname() != null) {
                                participantName = user.getNickname();
                            }
                        } catch (Exception e) {
                            logger.warn("获取用户信息失败: userId={}", participantId);
                        }
                    }

                    participantsJson.append(String.format(
                        "{\"userId\":\"%s\",\"username\":\"%s\"}",
                        participantId, participantName
                    ));
                    first = false;
                }
            }
            participantsJson.append("]");

            String json = String.format(
                "{\"type\":\"existing_participants\",\"conferenceId\":%d,\"participants\":%s}",
                conferenceId, participantsJson.toString()
            );

            session.sendMessage(new TextMessage(json));
            logger.info("📤 已发送已有参与者列表: conferenceId={}, 参与者数={}", conferenceId, participants.size() - 1);

        } catch (Exception e) {
            logger.error("❌ 发送已有参与者列表失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 广播参与者加入消息
     */
    private void broadcastParticipantJoined(Long conferenceId, Long userId, String username) {
        try {
            String nickname = username;
            if (userService != null) {
                try {
                    var user = userService.getUserById(userId);
                    if (user != null && user.getNickname() != null) {
                        nickname = user.getNickname();
                    }
                } catch (Exception e) {
                    logger.warn("获取用户昵称失败: userId={}", userId);
                }
            }

            String json = String.format(
                "{\"type\":\"participant_joined\",\"conferenceId\":%d,\"userId\":\"%s\",\"username\":\"%s\",\"nickname\":\"%s\"}",
                conferenceId, userId, username, nickname
            );

            broadcastToConference(conferenceId, json, userId);
            logger.info("📢 已广播参与者加入: conferenceId={}, userId={}, username={}", conferenceId, userId, username);

        } catch (Exception e) {
            logger.error("❌ 广播参与者加入失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理离开会议
     */
    private void handleLeaveConference(WebSocketSession session, Map<String, Object> msg) {
        try {
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;

            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long userId = Long.parseLong(userIdStr);

            logger.info("🚪 用户离开会议: conferenceId={}, userId={}", conferenceId, userId);

            // 从会议参与者列表中移除
            Set<Long> participants = conferenceParticipants.get(conferenceId);
            if (participants != null) {
                participants.remove(userId);
                if (participants.isEmpty()) {
                    conferenceParticipants.remove(conferenceId);
                }
            }

            // 移除用户会话映射
            userSessions.remove(userId);

            // 广播参与者离开消息
            String username = "user" + userId;
            if (userService != null) {
                try {
                    var user = userService.getUserById(userId);
                    if (user != null && user.getNickname() != null) {
                        username = user.getNickname();
                    }
                } catch (Exception e) {
                    logger.warn("获取用户信息失败: userId={}", userId);
                }
            }

            String json = String.format(
                "{\"type\":\"participant_left\",\"conferenceId\":%d,\"userId\":\"%s\",\"username\":\"%s\"}",
                conferenceId, userId, username
            );

            broadcastToConference(conferenceId, json, null);
            logger.info("✅ 用户成功离开会议: conferenceId={}, userId={}", conferenceId, userId);

        } catch (Exception e) {
            logger.error("❌ 处理离开会议失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理会议消息
     */
    private void handleConferenceMessage(WebSocketSession session, Map<String, Object> msg) {
        try {
            // 修复: 兼容扁平结构和data嵌套结构
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;
            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));
            String username = (String) data.get("username");
            String message = (String) data.get("message");

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long senderId = Long.parseLong(userIdStr);

            logger.info("💬 会议消息: conferenceId={}, sender={}, message={}", conferenceId, username, message);

            // 广播消息给会议中的其他人
            String json = String.format(
                "{\"type\":\"conference_message\",\"conferenceId\":%d,\"senderId\":\"%s\",\"senderName\":\"%s\",\"message\":\"%s\"}",
                conferenceId, senderId, username, message.replace("\"", "\\\"")
            );

            broadcastToConference(conferenceId, json, senderId);

        } catch (Exception e) {
            logger.error("❌ 处理会议消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理媒体状态更新
     */
    private void handleMediaStatusUpdate(WebSocketSession session, Map<String, Object> msg) {
        try {
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;

            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long userId = Long.parseLong(userIdStr);

            logger.info("🎥 媒体状态更新: conferenceId={}, userId={}", conferenceId, userId);

            // 转发给会议中的其他人
            String json = objectMapper.writeValueAsString(msg);
            broadcastToConference(conferenceId, json, userId);

        } catch (Exception e) {
            logger.error("❌ 处理媒体状态更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理屏幕共享开始
     */
    private void handleScreenShareStart(WebSocketSession session, Map<String, Object> msg) {
        try {
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;

            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long userId = Long.parseLong(userIdStr);

            logger.info("📺 屏幕共享开始: conferenceId={}, userId={}", conferenceId, userId);

            // 转发给会议中的其他人
            String json = objectMapper.writeValueAsString(msg);
            broadcastToConference(conferenceId, json, userId);

        } catch (Exception e) {
            logger.error("❌ 处理屏幕共享开始失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理屏幕共享停止
     */
    private void handleScreenShareStop(WebSocketSession session, Map<String, Object> msg) {
        try {
            Map<String, Object> data = msg.containsKey("data") ? (Map<String, Object>) msg.get("data") : msg;

            String conferenceIdStr = String.valueOf(data.get("conferenceId"));
            String userIdStr = String.valueOf(data.get("userId"));

            Long conferenceId = Long.parseLong(conferenceIdStr);
            Long userId = Long.parseLong(userIdStr);

            logger.info("📺 屏幕共享停止: conferenceId={}, userId={}", conferenceId, userId);

            // 转发给会议中的其他人
            String json = objectMapper.writeValueAsString(msg);
            broadcastToConference(conferenceId, json, userId);

        } catch (Exception e) {
            logger.error("❌ 处理屏幕共享停止失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 广播消息到指定会议的所有参与者（可选排除发送者）
     */
    private void broadcastToConference(Long conferenceId, String message, Long excludeUserId) {
        try {
            Set<Long> participants = conferenceParticipants.get(conferenceId);
            if (participants == null || participants.isEmpty()) {
                logger.warn("⚠️ 会议无参与者，跳过广播: conferenceId={}", conferenceId);
                return;
            }

            int sentCount = 0;
            for (Long participantId : participants) {
                if (excludeUserId != null && participantId.equals(excludeUserId)) {
                    continue; // 跳过发送者自己
                }

                String sessionId = userSessions.get(participantId);
                if (sessionId != null) {
                    WebSocketSession session = sessions.get(sessionId);
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(message));
                        sentCount++;
                    }
                }
            }

            logger.debug("📤 广播消息完成: conferenceId={}, 发送给{}个参与者", conferenceId, sentCount);

        } catch (Exception e) {
            logger.error("❌ 广播消息失败: conferenceId={}", conferenceId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        // 查找并清理该session对应的用户
        Long userId = null;
        for (Map.Entry<Long, String> entry : userSessions.entrySet()) {
            if (entry.getValue().equals(sessionId)) {
                userId = entry.getKey();
                break;
            }
        }

        if (userId != null) {
            userSessions.remove(userId);

            // 从所有会议中移除该用户
            for (Map.Entry<Long, Set<Long>> entry : conferenceParticipants.entrySet()) {
                Long conferenceId = entry.getKey();
                Set<Long> participants = entry.getValue();
                if (participants.remove(userId)) {
                    logger.info("🚪 用户断开连接，自动离开会议: userId={}, conferenceId={}", userId, conferenceId);

                    // 广播离开消息
                    String json = String.format(
                        "{\"type\":\"participant_left\",\"conferenceId\":%d,\"userId\":\"%s\",\"username\":\"user%s\"}",
                        conferenceId, userId, userId
                    );
                    broadcastToConference(conferenceId, json, null);

                    if (participants.isEmpty()) {
                        conferenceParticipants.remove(conferenceId);
                    }
                }
            }
        }

        logger.info("❌ WebSocket 连接关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("⚠️ WebSocket 传输错误: sessionId={}", session.getId(), exception);
        session.close();
    }

    /**
     * 广播消息到所有连接
     */
    public void broadcast(String message) throws Exception {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                session.sendMessage(textMessage);
            }
        }
    }

    /**
     * 发送消息到指定会话
     */
    public void sendToSession(String sessionId, String message) throws Exception {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
}
