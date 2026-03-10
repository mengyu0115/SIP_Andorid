package com.sip.server.config;

import lombok.Data;

/**
 * SIP账号实体
 *
 * 表示一个预注册的SIP账号信息
 */
@Data
public class SipAccount {

    /**
     * SIP用户名
     */
    private String username;

    /**
     * SIP密码
     */
    private String password;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * SIP URI (自动生成)
     * 格式: sip:username@domain
     */
    private String sipUri;

    /**
     * 是否已分配
     */
    private boolean allocated = false;

    /**
     * 分配给的应用用户ID
     */
    private Long userId;

    /**
     * 分配给的应用用户名
     */
    private String appUsername;

    public SipAccount() {
    }

    public SipAccount(String username, String password, String displayName) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    /**
     * 生成完整的SIP URI
     */
    public void generateSipUri(String domain) {
        this.sipUri = "sip:" + username + "@" + domain;
    }

    /**
     * 标记为已分配
     */
    public void allocate(Long userId, String appUsername) {
        this.allocated = true;
        this.userId = userId;
        this.appUsername = appUsername;
    }

    /**
     * 释放分配
     */
    public void release() {
        this.allocated = false;
        this.userId = null;
        this.appUsername = null;
    }
}
