package com.example.myapplication.model;

/**
 * 用户信息
 *
 * 对应服务端 com.sip.server.entity.User：
 * { id, username, nickname, avatar, status, sipUri, sipPassword, ... }
 */
public class UserInfo {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer status;
    private String sipUri;
    private String sipPassword;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getSipUri() { return sipUri; }
    public void setSipUri(String sipUri) { this.sipUri = sipUri; }

    public String getSipPassword() { return sipPassword; }
    public void setSipPassword(String sipPassword) { this.sipPassword = sipPassword; }
}
