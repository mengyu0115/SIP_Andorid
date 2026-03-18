package com.example.myapplication.model;

import org.json.JSONObject;

/**
 * 会议信息模型
 * 对应 PC 端 ConferenceDTO
 */
public class ConferenceInfo {

    private Long id;
    private String conferenceCode;
    private String conferenceUri;
    private String title;
    private Long creatorId;
    private Integer status;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private String startTime;
    private String endTime;

    public static ConferenceInfo fromJson(JSONObject json) {
        ConferenceInfo info = new ConferenceInfo();
        info.setId(json.optLong("id", -1L));
        info.setConferenceCode(json.optString("conferenceCode", null));
        info.setConferenceUri(json.optString("conferenceUri", null));
        info.setTitle(json.optString("title", null));
        info.setCreatorId(json.optLong("creatorId", -1L));
        info.setStatus(json.optInt("status", 0));
        info.setMaxParticipants(json.optInt("maxParticipants", 10));
        info.setCurrentParticipants(json.optInt("currentParticipants", 0));
        info.setStartTime(json.optString("startTime", null));
        info.setEndTime(json.optString("endTime", null));
        return info;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConferenceCode() { return conferenceCode; }
    public void setConferenceCode(String conferenceCode) { this.conferenceCode = conferenceCode; }

    public String getConferenceUri() { return conferenceUri; }
    public void setConferenceUri(String conferenceUri) { this.conferenceUri = conferenceUri; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }

    public Integer getCurrentParticipants() { return currentParticipants; }
    public void setCurrentParticipants(Integer currentParticipants) { this.currentParticipants = currentParticipants; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
