package com.example.myapplication.model;

import org.json.JSONObject;

/**
 * 会议参与者信息
 * 对应 PC 端 ParticipantDTO
 */
public class ConferenceParticipantInfo {

    private Long userId;
    private String username;
    private String nickname;
    private Integer role;
    private Boolean audioEnabled;
    private Boolean videoEnabled;

    public static ConferenceParticipantInfo fromJson(JSONObject json) {
        ConferenceParticipantInfo info = new ConferenceParticipantInfo();
        info.setUserId(json.optLong("userId", -1L));
        info.setUsername(json.optString("username", null));
        info.setNickname(json.optString("nickname", null));
        info.setRole(json.optInt("role", 0));
        info.setAudioEnabled(json.optBoolean("audioEnabled", false));
        info.setVideoEnabled(json.optBoolean("videoEnabled", false));
        return info;
    }

    public String getDisplayName() {
        if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        }
        return username;
    }

    // Getters and Setters

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Integer getRole() { return role; }
    public void setRole(Integer role) { this.role = role; }

    public Boolean getAudioEnabled() { return audioEnabled; }
    public void setAudioEnabled(Boolean audioEnabled) { this.audioEnabled = audioEnabled; }

    public Boolean getVideoEnabled() { return videoEnabled; }
    public void setVideoEnabled(Boolean videoEnabled) { this.videoEnabled = videoEnabled; }
}
