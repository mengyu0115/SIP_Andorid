package com.sip.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sip.common.dto.MediaFrameMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 媒体转发服务 - 用于实时会议的音视频转发
 *
 * 功能:
 * 1. 接收客户端的媒体帧（音频/视频/屏幕共享）
 * 2. 转发给同一会议的其他参与者
 * 3. 管理会议参与者会话
 *
 * @author SIP Team
 * @since 2025-12-09
 */
@Service
public class ConferenceMediaRelayService {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceMediaRelayService.class);

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会议参与者映射: conferenceId -> Set<userId>
     */
    private final Map<Long, Set<Long>> conferenceParticipants = new ConcurrentHashMap<>();

    /**
     * 用户会话映射: userId -> sessionId
     */
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();

    /**
     * 帧序号计数器: userId -> sequence
     */
    private final Map<Long, Long> sequenceCounters = new ConcurrentHashMap<>();

    /**
     * 添加参与者到会议
     */
    public void addParticipant(Long conferenceId, Long userId, String sessionId) {
        conferenceParticipants.computeIfAbsent(conferenceId, k -> new CopyOnWriteArraySet<>()).add(userId);
        userSessions.put(userId, sessionId);
        logger.info("✅ 参与者加入媒体会议: conferenceId={}, userId={}, sessionId={}",
            conferenceId, userId, sessionId);

        // 通知其他参与者有新人加入
        broadcastParticipantList(conferenceId);
    }

    /**
     * 从会议移除参与者
     */
    public void removeParticipant(Long conferenceId, Long userId) {
        Set<Long> participants = conferenceParticipants.get(conferenceId);
        if (participants != null) {
            participants.remove(userId);
            if (participants.isEmpty()) {
                conferenceParticipants.remove(conferenceId);
                logger.info("🗑️  会议参与者为空，清理会议: conferenceId={}", conferenceId);
            }
        }
        userSessions.remove(userId);
        sequenceCounters.remove(userId);
        logger.info("❌ 参与者离开媒体会议: conferenceId={}, userId={}", conferenceId, userId);

        // 通知其他参与者有人离开
        if (participants != null && !participants.isEmpty()) {
            broadcastParticipantList(conferenceId);
        }
    }

    /**
     * 获取会议参与者数量
     */
    public int getParticipantCount(Long conferenceId) {
        Set<Long> participants = conferenceParticipants.get(conferenceId);
        return participants != null ? participants.size() : 0;
    }

    /**
     * 转发媒体帧
     */
    public void relayMediaFrame(MediaFrameMessage message) {
        if (messagingTemplate == null) {
            logger.warn("⚠️ WebSocket未启用，无法转发媒体帧");
            return;
        }

        Long conferenceId = message.getConferenceId();
        Long senderId = message.getUserId();

        Set<Long> participants = conferenceParticipants.get(conferenceId);
        if (participants == null || participants.isEmpty()) {
            logger.warn("⚠️ 会议无参与者，跳过转发: conferenceId={}", conferenceId);
            return;
        }

        // 设置序号
        Long sequence = sequenceCounters.compute(senderId, (k, v) -> v == null ? 1L : v + 1);
        message.setSequence(sequence);

        // 转发给除发送者外的所有参与者
        int relayCount = 0;
        for (Long participantId : participants) {
            if (!participantId.equals(senderId)) {
                try {
                    String destination = "/user/" + participantId + "/queue/media";
                    messagingTemplate.convertAndSend(destination, message);
                    relayCount++;
                } catch (Exception e) {
                    logger.error("❌ 转发媒体帧失败: participantId={}, error={}", participantId, e.getMessage());
                }
            }
        }

        // 降低日志频率 - 每100帧记录一次
        if (sequence % 100 == 0) {
            logger.debug("📤 媒体帧已转发: type={}, sender={}, relayCount={}, seq={}",
                message.getMediaType(), senderId, relayCount, sequence);
        }
    }

    /**
     * 广播参与者列表
     */
    private void broadcastParticipantList(Long conferenceId) {
        if (messagingTemplate == null) {
            return;
        }

        Set<Long> participants = conferenceParticipants.get(conferenceId);
        if (participants == null) {
            return;
        }

        Map<String, Object> message = Map.of(
            "type", "PARTICIPANT_LIST_UPDATE",
            "conferenceId", conferenceId,
            "participants", participants,
            "count", participants.size(),
            "timestamp", System.currentTimeMillis()
        );

        String destination = "/topic/conference/" + conferenceId;
        messagingTemplate.convertAndSend(destination, message);

        logger.info("📢 广播参与者列表: conferenceId={}, count={}", conferenceId, participants.size());
    }

    /**
     * 获取会议的所有参与者
     */
    public Set<Long> getParticipants(Long conferenceId) {
        return conferenceParticipants.get(conferenceId);
    }

    /**
     * 清理会议资源
     */
    public void cleanupConference(Long conferenceId) {
        conferenceParticipants.remove(conferenceId);
        logger.info("🧹 清理会议资源: conferenceId={}", conferenceId);
    }

    /**
     * 检查用户是否在会议中
     */
    public boolean isUserInConference(Long conferenceId, Long userId) {
        Set<Long> participants = conferenceParticipants.get(conferenceId);
        return participants != null && participants.contains(userId);
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "activeConferences", conferenceParticipants.size(),
            "totalParticipants", userSessions.size(),
            "conferences", conferenceParticipants.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().size()
                ))
        );
    }
}
