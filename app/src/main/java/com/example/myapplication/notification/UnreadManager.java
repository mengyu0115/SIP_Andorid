package com.example.myapplication.notification;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存未读消息计数管理器（单例）
 *
 * 跟踪每个用户的未读消息数，并通知 UI 更新角标。
 * 配合 ConversationFragment（列表角标）和 MainActivity（BottomNav badge）使用。
 */
public class UnreadManager {

    public interface UnreadCountChangeListener {
        /** 未读总数发生变化 */
        void onUnreadCountChanged(int totalUnread);
    }

    private static UnreadManager instance;

    /** fromUsername → 未读数 */
    private final ConcurrentHashMap<String, AtomicInteger> unreadCounts = new ConcurrentHashMap<>();

    /** 当前正在聊天的用户名（SIP ID），null 表示没有打开任何聊天 */
    private volatile String currentChatUsername;

    private final CopyOnWriteArrayList<UnreadCountChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static UnreadManager getInstance() {
        if (instance == null) {
            synchronized (UnreadManager.class) {
                if (instance == null) instance = new UnreadManager();
            }
        }
        return instance;
    }

    private UnreadManager() {}

    /** 递增指定用户的未读数 */
    public void incrementUnread(String fromUsername) {
        unreadCounts.computeIfAbsent(fromUsername, k -> new AtomicInteger(0)).incrementAndGet();
        notifyListeners();
    }

    /** 清零指定用户的未读数（打开聊天时调用） */
    public void clearUnread(String username) {
        AtomicInteger count = unreadCounts.get(username);
        if (count != null && count.get() > 0) {
            count.set(0);
            notifyListeners();
        }
    }

    /** 获取指定用户的未读数 */
    public int getUnreadCount(String username) {
        AtomicInteger count = unreadCounts.get(username);
        return count != null ? count.get() : 0;
    }

    /** 获取所有用户的未读总数 */
    public int getTotalUnread() {
        int total = 0;
        for (AtomicInteger count : unreadCounts.values()) {
            total += count.get();
        }
        return total;
    }

    /** 设置当前正在聊天的用户名（SIP ID） */
    public void setCurrentChatUsername(String username) {
        this.currentChatUsername = username;
    }

    /** 判断当前是否在与指定用户聊天 */
    public boolean isChatOpenFor(String username) {
        return username != null && username.equals(currentChatUsername);
    }

    /** 添加未读数变化监听器 */
    public void addListener(UnreadCountChangeListener listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    /** 移除未读数变化监听器 */
    public void removeListener(UnreadCountChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    private void notifyListeners() {
        int total = getTotalUnread();
        mainHandler.post(() -> {
            for (UnreadCountChangeListener listener : changeListeners) {
                try {
                    listener.onUnreadCountChanged(total);
                } catch (Exception ignored) {}
            }
        });
    }
}
