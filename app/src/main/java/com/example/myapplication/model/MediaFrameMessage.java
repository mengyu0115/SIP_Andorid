package com.example.myapplication.model;

import org.json.JSONObject;

/**
 * 媒体帧消息 - 用于 WebSocket 传输
 * 字段与 PC 端 com.sip.common.dto.MediaFrameMessage 完全一致
 */
public class MediaFrameMessage {

    private String type;
    private Long conferenceId;
    private Long userId;
    private String username;
    private String mediaType;
    private String frameData;
    private Integer width;
    private Integer height;
    private Long timestamp;
    private Long sequence;
    private Integer dataLength;

    public MediaFrameMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            if (mediaType != null) json.put("mediaType", mediaType);
            if (frameData != null) json.put("frameData", frameData);
            if (width != null) json.put("width", width);
            if (height != null) json.put("height", height);
            if (timestamp != null) json.put("timestamp", timestamp);
            if (sequence != null) json.put("sequence", sequence);
            if (dataLength != null) json.put("dataLength", dataLength);
        } catch (Exception e) {
            // ignore
        }
        return json;
    }

    public static MediaFrameMessage fromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            MediaFrameMessage msg = new MediaFrameMessage();
            msg.setType(json.optString("type", null));
            msg.setConferenceId(json.optLong("conferenceId", 0L));
            msg.setUserId(json.optLong("userId", 0L));
            msg.setUsername(json.optString("username", null));
            msg.setMediaType(json.optString("mediaType", null));
            msg.setFrameData(json.optString("frameData", null));
            if (json.has("width")) msg.setWidth(json.optInt("width"));
            if (json.has("height")) msg.setHeight(json.optInt("height"));
            if (json.has("timestamp")) msg.setTimestamp(json.optLong("timestamp"));
            if (json.has("sequence")) msg.setSequence(json.optLong("sequence"));
            if (json.has("dataLength")) msg.setDataLength(json.optInt("dataLength"));
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    // Getters and Setters

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getConferenceId() { return conferenceId; }
    public void setConferenceId(Long conferenceId) { this.conferenceId = conferenceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getFrameData() { return frameData; }
    public void setFrameData(String frameData) { this.frameData = frameData; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Long getSequence() { return sequence; }
    public void setSequence(Long sequence) { this.sequence = sequence; }

    public Integer getDataLength() { return dataLength; }
    public void setDataLength(Integer dataLength) { this.dataLength = dataLength; }

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
