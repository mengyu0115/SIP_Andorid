package com.sip.common.dto;

import java.io.Serializable;

/**
 * 媒体帧消息 - 用于WebSocket传输
 *
 * @author SIP Team
 * @since 2025-12-09
 */
public class MediaFrameMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 会议ID
     */
    private Long conferenceId;

    /**
     * 发送者用户ID
     */
    private Long userId;

    /**
     * 发送者用户名
     */
    private String username;

    /**
     * 媒体类型: AUDIO, VIDEO, SCREEN
     */
    private String mediaType;

    /**
     * 帧数据 (Base64编码)
     */
    private String frameData;

    /**
     * 帧宽度（仅视频）
     */
    private Integer width;

    /**
     * 帧高度（仅视频）
     */
    private Integer height;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 帧序号（用于排序）
     */
    private Long sequence;

    /**
     * 数据长度（仅音频）
     */
    private Integer dataLength;

    // 构造函数
    public MediaFrameMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(Long conferenceId) {
        this.conferenceId = conferenceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getFrameData() {
        return frameData;
    }

    public void setFrameData(String frameData) {
        this.frameData = frameData;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    public void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    @Override
    public String toString() {
        return "MediaFrameMessage{" +
                "type='" + type + '\'' +
                ", conferenceId=" + conferenceId +
                ", userId=" + userId +
                ", username='" + username + '\'' +
                ", mediaType='" + mediaType + '\'' +
                ", dataLength=" + (dataLength != null ? dataLength : (frameData != null ? frameData.length() : 0)) +
                ", width=" + width +
                ", height=" + height +
                ", timestamp=" + timestamp +
                ", sequence=" + sequence +
                '}';
    }
}
