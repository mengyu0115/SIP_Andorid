package com.example.myapplication.adapter;

/**
 * 联系人列表数据模型
 *
 * 对应 PC 端 MainController.refreshUserList() 中的 "online:username" / "offline:username" 数据格式。
 * 用于 ContactsFragment 展示所有用户及其在线状态。
 */
public class ContactItem {

    private final long userId;
    private final String username;
    private final boolean online;

    public ContactItem(long userId, String username, boolean online) {
        this.userId = userId;
        this.username = username;
        this.online = online;
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }
}
