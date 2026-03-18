package com.sip.client.conference;

// import com.sip.entity.ConferenceParticipant;
// import com.sip.mapper.ConferenceParticipantMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议参与者管理器 (Participant Manager)
 *
 * 负责管理会议中的参与者：
 * 1. 参与者状态跟踪（在线/离线/静音等）
 * 2. 角色管理（主持人/普通参与者）
 * 3. 参与者能力协商（音频/视频/共享屏幕）
 * 4. 参与者事件通知
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
public class ParticipantManager {

    // 可选的数据库Mapper，如果为null则不持久化（用于测试）
    // private ConferenceParticipantMapper participantMapper;

    /**
     * 会议ID到参与者列表的映射
     * Key: conferenceId, Value: Map<userId, ParticipantInfo>
     */
    private final Map<Long, Map<Long, ParticipantInfo>> conferenceParticipants = new ConcurrentHashMap<>();

    /**
     * 参与者状态枚举
     */
    public enum ParticipantStatus {
        JOINING,        // 正在加入
        CONNECTED,      // 已连接
        DISCONNECTED,   // 已断开
        KICKED          // 被移除
    }

    /**
     * 参与者角色枚举
     */
    public enum ParticipantRole {
        HOST,           // 主持人
        MODERATOR,      // 协管员
        PARTICIPANT     // 普通参与者
    }

    /**
     * 添加参与者到会议
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param userName 用户名
     * @param sipUri SIP URI
     * @param isHost 是否为主持人
     * @return 参与者信息
     */
    public ParticipantInfo addParticipant(Long conferenceId, Long userId, String userName,
                                         String sipUri, boolean isHost) {
        System.out.println("[ParticipantManager] 添加参与者: conferenceId=" + conferenceId +
            ", userId=" + userId + ", userName=" + userName + ", isHost=" + isHost);

        // 创建参与者信息
        ParticipantInfo info = new ParticipantInfo(userId, userName, sipUri);
        info.setRole(isHost ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT);
        info.setStatus(ParticipantStatus.JOINING);

        // 添加到内存映射
        conferenceParticipants
            .computeIfAbsent(conferenceId, k -> new ConcurrentHashMap<>())
            .put(userId, info);

        // 持久化到数据库（如果Mapper可用）
        /* 测试模式下注释掉数据库操作
        if (participantMapper != null) {
            try {
                ConferenceParticipant participant = new ConferenceParticipant();
                participant.setConferenceId(conferenceId);
                participant.setUserId(userId);
                participant.setJoinedAt(new Date());
                participantMapper.insert(participant);
            } catch (Exception e) {
                System.err.println("[ParticipantManager ERROR] 保存参与者到数据库失败: " + e.getMessage());
            }
        }
        */

        System.out.println("[ParticipantManager] 参与者添加成功: {}");
        return info;
    }

    /**
     * 移除参与者
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @return 是否移除成功
     */
    public boolean removeParticipant(Long conferenceId, Long userId) {
        System.out.println("[ParticipantManager] 移除参与者: conferenceId={}, userId={}");

        Map<Long, ParticipantInfo> participants = conferenceParticipants.get(conferenceId);
        if (participants == null) {
            System.out.println("[ParticipantManager WARN] 会议不存在: conferenceId={}");
            return false;
        }

        ParticipantInfo info = participants.remove(userId);
        if (info != null) {
            info.setStatus(ParticipantStatus.DISCONNECTED);
            info.setLeftTime(new Date());

            // 更新数据库（如果Mapper可用）
            /* 测试模式下注释掉数据库操作
            if (participantMapper != null) {
                try {
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("conference_id", conferenceId);
                    queryMap.put("user_id", userId);
                    List<ConferenceParticipant> dbParticipants = participantMapper.selectByMap(queryMap);
                    if (!dbParticipants.isEmpty()) {
                        ConferenceParticipant participant = dbParticipants.get(0);
                        participant.setLeftAt(new Date());
                        participantMapper.updateById(participant);
                    }
                } catch (Exception e) {
                    System.err.println("[ParticipantManager ERROR] 更新参与者数据库记录失败: " + e.getMessage());
                }
            }
            */

            System.out.println("[ParticipantManager] 参与者移除成功: {}");

            // 如果会议无参与者，清理映射
            if (participants.isEmpty()) {
                conferenceParticipants.remove(conferenceId);
            }

            return true;
        }

        System.out.println("[ParticipantManager WARN] 参与者不存在: userId={}");
        return false;
    }

    /**
     * 更新参与者状态
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param status 新状态
     */
    public void updateParticipantStatus(Long conferenceId, Long userId, ParticipantStatus status) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 更新参与者状态: userId=" + userId +
                ", " + info.getStatus() + " -> " + status);
            info.setStatus(status);
        }
    }

    /**
     * 设置参与者角色
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param role 角色
     */
    public void setParticipantRole(Long conferenceId, Long userId, ParticipantRole role) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 设置参与者角色: userId=" + userId +
                ", " + info.getRole() + " -> " + role);
            info.setRole(role);
        }
    }

    /**
     * 设置参与者静音状态
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param muted 是否静音
     */
    public void setAudioMuted(Long conferenceId, Long userId, boolean muted) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 设置参与者静音: userId={}, muted={}");
            info.setAudioMuted(muted);
        }
    }

    /**
     * 设置参与者视频状态
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param enabled 是否开启视频
     */
    public void setVideoEnabled(Long conferenceId, Long userId, boolean enabled) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 设置参与者视频: userId={}, enabled={}");
            info.setVideoEnabled(enabled);
        }
    }

    /**
     * 设置参与者屏幕共享状态
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param sharing 是否共享屏幕
     */
    public void setScreenSharing(Long conferenceId, Long userId, boolean sharing) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 设置参与者屏幕共享: userId={}, sharing={}");
            info.setScreenSharing(sharing);
        }
    }

    /**
     * 获取参与者信息
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @return 参与者信息，不存在返回null
     */
    public ParticipantInfo getParticipant(Long conferenceId, Long userId) {
        Map<Long, ParticipantInfo> participants = conferenceParticipants.get(conferenceId);
        return participants != null ? participants.get(userId) : null;
    }

    /**
     * 获取会议的所有参与者
     *
     * @param conferenceId 会议ID
     * @return 参与者列表
     */
    public List<ParticipantInfo> getParticipants(Long conferenceId) {
        Map<Long, ParticipantInfo> participants = conferenceParticipants.get(conferenceId);
        return participants != null
            ? new ArrayList<>(participants.values())
            : new ArrayList<>();
    }

    /**
     * 获取会议的在线参与者数量
     *
     * @param conferenceId 会议ID
     * @return 在线参与者数量
     */
    public int getActiveParticipantCount(Long conferenceId) {
        return (int) getParticipants(conferenceId).stream()
            .filter(p -> p.getStatus() == ParticipantStatus.CONNECTED)
            .count();
    }

    /**
     * 获取会议的主持人
     *
     * @param conferenceId 会议ID
     * @return 主持人信息，不存在返回null
     */
    public ParticipantInfo getHost(Long conferenceId) {
        return getParticipants(conferenceId).stream()
            .filter(p -> p.getRole() == ParticipantRole.HOST)
            .findFirst()
            .orElse(null);
    }

    /**
     * 判断用户是否为主持人
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @return 是否为主持人
     */
    public boolean isHost(Long conferenceId, Long userId) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        return info != null && info.getRole() == ParticipantRole.HOST;
    }

    /**
     * 判断用户是否有管理权限（主持人或协管员）
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @return 是否有管理权限
     */
    public boolean hasModerationRights(Long conferenceId, Long userId) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        return info != null && (info.getRole() == ParticipantRole.HOST
            || info.getRole() == ParticipantRole.MODERATOR);
    }

    /**
     * 踢出参与者
     *
     * @param conferenceId 会议ID
     * @param userId 要踢出的用户ID
     * @param operatorId 操作者ID
     * @return 是否成功
     */
    public boolean kickParticipant(Long conferenceId, Long userId, Long operatorId) {
        // 检查操作者权限
        if (!hasModerationRights(conferenceId, operatorId)) {
            System.out.println("[ParticipantManager WARN] 操作者无权限踢出参与者: operatorId={}");
            return false;
        }

        // 不能踢出主持人
        if (isHost(conferenceId, userId)) {
            System.out.println("[ParticipantManager WARN] 不能踢出主持人");
            return false;
        }

        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 踢出参与者: userId={}, operatorId={}");
            info.setStatus(ParticipantStatus.KICKED);
            removeParticipant(conferenceId, userId);
            return true;
        }

        return false;
    }

    /**
     * 全体静音
     *
     * @param conferenceId 会议ID
     * @param operatorId 操作者ID
     * @return 被静音的参与者数量
     */
    public int muteAll(Long conferenceId, Long operatorId) {
        // 检查操作者权限
        if (!hasModerationRights(conferenceId, operatorId)) {
            System.out.println("[ParticipantManager WARN] 操作者无权限全体静音: operatorId={}");
            return 0;
        }

        System.out.println("[ParticipantManager] 全体静音: conferenceId={}, operatorId={}");

        int count = 0;
        for (ParticipantInfo info : getParticipants(conferenceId)) {
            // 不静音主持人和协管员
            if (info.getRole() != ParticipantRole.HOST
                && info.getRole() != ParticipantRole.MODERATOR) {
                info.setAudioMuted(true);
                count++;
            }
        }

        System.out.println("[ParticipantManager] 全体静音完成: 静音{}人");
        return count;
    }

    /**
     * 清空会议的所有参与者
     *
     * @param conferenceId 会议ID
     */
    public void clearConference(Long conferenceId) {
        System.out.println("[ParticipantManager] 清空会议参与者: conferenceId={}");
        conferenceParticipants.remove(conferenceId);
    }

    /**
     * 获取参与者的能力信息
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @return 能力信息
     */
    public ParticipantCapabilities getCapabilities(Long conferenceId, Long userId) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        return info != null ? info.getCapabilities() : null;
    }

    /**
     * 更新参与者能力
     *
     * @param conferenceId 会议ID
     * @param userId 用户ID
     * @param capabilities 能力信息
     */
    public void updateCapabilities(Long conferenceId, Long userId, ParticipantCapabilities capabilities) {
        ParticipantInfo info = getParticipant(conferenceId, userId);
        if (info != null) {
            System.out.println("[ParticipantManager] 更新参与者能力: userId={}, capabilities={}");
            info.setCapabilities(capabilities);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 参与者信息类
     */
    public static class ParticipantInfo {
        private final Long userId;
        private final String userName;
        private final String sipUri;
        private ParticipantRole role;
        private ParticipantStatus status;
        private boolean audioMuted;
        private boolean videoEnabled;
        private boolean screenSharing;
        private ParticipantCapabilities capabilities;
        private Date joinedTime;
        private Date leftTime;

        public ParticipantInfo(Long userId, String userName, String sipUri) {
            this.userId = userId;
            this.userName = userName;
            this.sipUri = sipUri;
            this.role = ParticipantRole.PARTICIPANT;
            this.status = ParticipantStatus.JOINING;
            this.audioMuted = false;
            this.videoEnabled = true;
            this.screenSharing = false;
            this.capabilities = new ParticipantCapabilities();
            this.joinedTime = new Date();
        }

        // Getters and Setters
        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getSipUri() { return sipUri; }

        public ParticipantRole getRole() { return role; }
        public void setRole(ParticipantRole role) { this.role = role; }

        public ParticipantStatus getStatus() { return status; }
        public void setStatus(ParticipantStatus status) { this.status = status; }

        public boolean isAudioMuted() { return audioMuted; }
        public void setAudioMuted(boolean audioMuted) { this.audioMuted = audioMuted; }

        public boolean isVideoEnabled() { return videoEnabled; }
        public void setVideoEnabled(boolean videoEnabled) { this.videoEnabled = videoEnabled; }

        public boolean isScreenSharing() { return screenSharing; }
        public void setScreenSharing(boolean screenSharing) { this.screenSharing = screenSharing; }

        public ParticipantCapabilities getCapabilities() { return capabilities; }
        public void setCapabilities(ParticipantCapabilities capabilities) { this.capabilities = capabilities; }

        public Date getJoinedTime() { return joinedTime; }
        public void setJoinedTime(Date joinedTime) { this.joinedTime = joinedTime; }

        public Date getLeftTime() { return leftTime; }
        public void setLeftTime(Date leftTime) { this.leftTime = leftTime; }

        @Override
        public String toString() {
            return String.format("ParticipantInfo{userId=%d, userName='%s', role=%s, status=%s}",
                userId, userName, role, status);
        }
    }

    /**
     * 参与者能力类
     */
    public static class ParticipantCapabilities {
        private boolean supportsAudio = true;
        private boolean supportsVideo = true;
        private boolean supportsScreenShare = false;
        private boolean supportsChat = true;
        private List<String> audioCodecs = new ArrayList<>();
        private List<String> videoCodecs = new ArrayList<>();

        public boolean isSupportsAudio() { return supportsAudio; }
        public void setSupportsAudio(boolean supportsAudio) { this.supportsAudio = supportsAudio; }

        public boolean isSupportsVideo() { return supportsVideo; }
        public void setSupportsVideo(boolean supportsVideo) { this.supportsVideo = supportsVideo; }

        public boolean isSupportsScreenShare() { return supportsScreenShare; }
        public void setSupportsScreenShare(boolean supportsScreenShare) { this.supportsScreenShare = supportsScreenShare; }

        public boolean isSupportsChat() { return supportsChat; }
        public void setSupportsChat(boolean supportsChat) { this.supportsChat = supportsChat; }

        public List<String> getAudioCodecs() { return audioCodecs; }
        public void setAudioCodecs(List<String> audioCodecs) { this.audioCodecs = audioCodecs; }

        public List<String> getVideoCodecs() { return videoCodecs; }
        public void setVideoCodecs(List<String> videoCodecs) { this.videoCodecs = videoCodecs; }

        @Override
        public String toString() {
            return String.format("Capabilities{audio=%s, video=%s, screenShare=%s}",
                supportsAudio, supportsVideo, supportsScreenShare);
        }
    }
}
