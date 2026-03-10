package com.example.myapplication.network;

import java.util.Collections;
import java.util.Set;

/**
 * 在线状态内存缓存（单例）
 *
 * 由 ConversationFragment 每 3 秒刷新时更新，
 * ChatActivity 发送消息时查询对方是否在线以决定 isOffline 标记。
 */
public class OnlineStatusCache {

    private static OnlineStatusCache instance;

    /** 当前在线用户 ID 集合（volatile 保证可见性） */
    private volatile Set<Long> onlineIds = Collections.emptySet();

    public static OnlineStatusCache getInstance() {
        if (instance == null) {
            synchronized (OnlineStatusCache.class) {
                if (instance == null) instance = new OnlineStatusCache();
            }
        }
        return instance;
    }

    private OnlineStatusCache() {}

    /** 更新在线用户集合（由 ConversationFragment.refreshUserList 调用） */
    public void updateOnlineIds(Set<Long> ids) {
        this.onlineIds = (ids != null) ? ids : Collections.emptySet();
    }

    /** 查询指定用户是否在线 */
    public boolean isOnline(long userId) {
        return onlineIds.contains(userId);
    }
}
