package com.example.myapplication.adapter;

/**
 * 会话列表数据模型（增加 userId 用于跳转 ChatActivity，增加 unreadCount 用于未读角标）
 */
public class ConversationItem {

    private final long userId;
    private final String name;
    private final String lastMessage;
    private final String time;
    private int unreadCount;

    public ConversationItem(long userId, String name, String lastMessage, String time) {
        this(userId, name, lastMessage, time, 0);
    }

    public ConversationItem(long userId, String name, String lastMessage, String time, int unreadCount) {
        this.userId = userId;
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
        this.unreadCount = unreadCount;
    }

    public long getUserId() { return userId; }
    public String getName() { return name; }
    public String getLastMessage() { return lastMessage; }
    public String getTime() { return time; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}
