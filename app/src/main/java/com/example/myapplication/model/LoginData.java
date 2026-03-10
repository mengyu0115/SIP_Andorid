package com.example.myapplication.model;

/**
 * 登录接口 data 字段
 *
 * 对应 POST /api/user/login 返回的 data：
 * {
 *   "token": "xxx",
 *   "expiresIn": 86400,
 *   "user": { id, username, nickname, avatar, sipUri, sipPassword, ... }
 * }
 */
public class LoginData {
    private String token;
    private int expiresIn;
    private UserInfo user;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getExpiresIn() { return expiresIn; }
    public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
