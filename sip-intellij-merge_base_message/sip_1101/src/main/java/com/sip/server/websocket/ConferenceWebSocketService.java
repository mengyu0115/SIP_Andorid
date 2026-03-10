package com.sip.server.websocket;

import com.sip.server.entity.ConferenceParticipant;
import com.sip.server.service.ConferenceServiceSimplified;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议WebSocket广播服务
 *
 * 功能：
 * 1. 参与者加入/离开会议时广播通知
 * 2. 会议状态变化时广播通知
 * 3. 实时同步会议参与者列表
 *
 * @author SIP Team
 * @date 2025-12-08
 */
@Slf4j
@Component
public class ConferenceWebSocketService {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ConferenceServiceSimplified conferenceService;

    /**
     * 广播参与者加入会议
     */
    public void broadcastParticipantJoined(Long conferenceId, Long userId, String username) {
        if (messagingTemplate == null) {
            log.warn("WebSocket未启用，无法广播参与者加入事件");
            return;
        }

        try {
            log.info("广播参与者加入会议: conferenceId={}, userId={}, username={}",
                conferenceId, userId, username);

            // 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "PARTICIPANT_JOINED");
            message.put("conferenceId", conferenceId);
            message.put("userId", userId);
            message.put("username", username);
            message.put("timestamp", System.currentTimeMillis());

            // 查询当前所有参与者
            List<ConferenceParticipant> participants = conferenceService.getParticipants(conferenceId);
            message.put("participantCount", participants.size());

            // 广播到会议频道
            String destination = "/topic/conference/" + conferenceId;
            messagingTemplate.convertAndSend(destination, message);

            log.info("✅ 已广播参与者加入事件到: {}", destination);

        } catch (Exception e) {
            log.error("广播参与者加入事件失败", e);
        }
    }

    /**
     * 广播参与者离开会议
     */
    public void broadcastParticipantLeft(Long conferenceId, Long userId, String username) {
        if (messagingTemplate == null) {
            log.warn("WebSocket未启用，无法广播参与者离开事件");
            return;
        }

        try {
            log.info("广播参与者离开会议: conferenceId={}, userId={}, username={}",
                conferenceId, userId, username);

            // 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "PARTICIPANT_LEFT");
            message.put("conferenceId", conferenceId);
            message.put("userId", userId);
            message.put("username", username);
            message.put("timestamp", System.currentTimeMillis());

            // 查询当前所有参与者
            List<ConferenceParticipant> participants = conferenceService.getParticipants(conferenceId);
            message.put("participantCount", participants.size());

            // 广播到会议频道
            String destination = "/topic/conference/" + conferenceId;
            messagingTemplate.convertAndSend(destination, message);

            log.info("✅ 已广播参与者离开事件到: {}", destination);

        } catch (Exception e) {
            log.error("广播参与者离开事件失败", e);
        }
    }

    /**
     * 广播会议状态变化
     */
    public void broadcastConferenceStatusChanged(Long conferenceId, Integer newStatus, String statusDesc) {
        if (messagingTemplate == null) {
            log.warn("WebSocket未启用，无法广播会议状态变化");
            return;
        }

        try {
            log.info("广播会议状态变化: conferenceId={}, newStatus={}, desc={}",
                conferenceId, newStatus, statusDesc);

            // 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "CONFERENCE_STATUS_CHANGED");
            message.put("conferenceId", conferenceId);
            message.put("status", newStatus);
            message.put("statusDesc", statusDesc);
            message.put("timestamp", System.currentTimeMillis());

            // 广播到会议频道
            String destination = "/topic/conference/" + conferenceId;
            messagingTemplate.convertAndSend(destination, message);

            log.info("✅ 已广播会议状态变化到: {}", destination);

        } catch (Exception e) {
            log.error("广播会议状态变化失败", e);
        }
    }

    /**
     * 广播会议结束
     */
    public void broadcastConferenceEnded(Long conferenceId, String reason) {
        if (messagingTemplate == null) {
            log.warn("WebSocket未启用，无法广播会议结束事件");
            return;
        }

        try {
            log.info("广播会议结束: conferenceId={}, reason={}", conferenceId, reason);

            // 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "CONFERENCE_ENDED");
            message.put("conferenceId", conferenceId);
            message.put("reason", reason != null ? reason : "会议已结束");
            message.put("timestamp", System.currentTimeMillis());

            // 广播到会议频道
            String destination = "/topic/conference/" + conferenceId;
            messagingTemplate.convertAndSend(destination, message);

            log.info("✅ 已广播会议结束事件到: {}", destination);

        } catch (Exception e) {
            log.error("广播会议结束事件失败", e);
        }
    }

    /**
     * 同步会议参与者列表
     */
    public void syncParticipants(Long conferenceId) {
        if (messagingTemplate == null) {
            log.warn("WebSocket未启用，无法同步参与者列表");
            return;
        }

        try {
            log.info("同步会议参与者列表: conferenceId={}", conferenceId);

            // 查询所有参与者
            List<ConferenceParticipant> participants = conferenceService.getParticipants(conferenceId);

            // 构建消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "PARTICIPANTS_SYNC");
            message.put("conferenceId", conferenceId);
            message.put("participants", participants);
            message.put("participantCount", participants.size());
            message.put("timestamp", System.currentTimeMillis());

            // 广播到会议频道
            String destination = "/topic/conference/" + conferenceId;
            messagingTemplate.convertAndSend(destination, message);

            log.info("✅ 已同步参与者列表到: {}, 人数: {}", destination, participants.size());

        } catch (Exception e) {
            log.error("同步参与者列表失败", e);
        }
    }
}
