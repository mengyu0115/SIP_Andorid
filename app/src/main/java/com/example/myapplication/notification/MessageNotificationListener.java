package com.example.myapplication.notification;

import android.util.Log;

import com.example.myapplication.SipMessageReceiver;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局消息通知监听器
 *
 * 桥接 SipMessageReceiver → 未读计数 + 系统通知。
 * 注册为 SipMessageReceiver 的 listener，当消息到达时：
 * 1. 如果当前正在与发送者聊天 → 跳过
 * 2. 递增未读计数
 * 3. 弹出系统通知
 */
public class MessageNotificationListener implements SipMessageReceiver.MessageCallback {

    private static final String TAG = "MsgNotifListener";

    private final NotificationHelper notificationHelper;

    /** username → UserDisplayInfo 缓存，由 ConversationFragment 填充 */
    private final ConcurrentHashMap<String, UserDisplayInfo> userDisplayInfoCache = new ConcurrentHashMap<>();

    /** 批量模式（离线消息加载时使用，避免大量通知同时弹出） */
    private volatile boolean batchMode = false;

    /** 批量模式下记录每个发送者的最后一条消息 */
    private final ConcurrentHashMap<String, String> batchMessages = new ConcurrentHashMap<>();

    public MessageNotificationListener(NotificationHelper notificationHelper) {
        this.notificationHelper = notificationHelper;
    }

    @Override
    public void onMessageReceived(String fromUsername, String content) {
        UnreadManager unreadManager = UnreadManager.getInstance();

        // 1. 如果当前正在与发送者聊天，跳过通知
        if (unreadManager.isChatOpenFor(fromUsername)) {
            return;
        }

        // 2. 递增未读计数
        unreadManager.incrementUnread(fromUsername);

        // 3. 弹出通知
        if (batchMode) {
            // 批量模式：记录最后一条，延迟到 flush 时统一弹
            batchMessages.put(fromUsername, content);
        } else {
            showNotificationForUser(fromUsername, content);
        }
    }

    /** 缓存用户显示信息（由 ConversationFragment 在刷新用户列表时调用） */
    public void cacheUserDisplayInfo(String sipUsername, long userId, String displayName) {
        userDisplayInfoCache.put(sipUsername, new UserDisplayInfo(userId, displayName));
    }

    /** 开启/关闭批量模式（离线消息加载前后调用） */
    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
        if (!batchMode) {
            // 关闭批量模式时不自动 flush，需要显式调用 flushBatchNotifications
        }
    }

    /** 批量模式结束后，为每个发送者弹一条汇总通知 */
    public void flushBatchNotifications() {
        for (ConcurrentHashMap.Entry<String, String> entry : batchMessages.entrySet()) {
            String fromUsername = entry.getKey();
            String lastContent = entry.getValue();
            int unreadCount = UnreadManager.getInstance().getUnreadCount(fromUsername);
            String preview;
            if (unreadCount > 1) {
                preview = "[" + unreadCount + "条新消息]";
            } else {
                preview = lastContent;
            }
            showNotificationForUser(fromUsername, preview);
        }
        batchMessages.clear();
    }

    private void showNotificationForUser(String fromUsername, String content) {
        UserDisplayInfo info = userDisplayInfoCache.get(fromUsername);
        long userId;
        String displayName;
        if (info != null) {
            userId = info.userId;
            displayName = info.displayName;
        } else {
            // 缓存未命中，用 fromUsername 作为 fallback
            try {
                userId = Long.parseLong(fromUsername);
            } catch (NumberFormatException e) {
                userId = fromUsername.hashCode();
            }
            displayName = "user" + fromUsername;
        }

        try {
            notificationHelper.showMessageNotification(fromUsername, userId, displayName, content);
        } catch (Exception e) {
            Log.e(TAG, "显示通知失败", e);
        }
    }

    /** 用户显示信息 */
    private static class UserDisplayInfo {
        final long userId;
        final String displayName;

        UserDisplayInfo(long userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
        }
    }
}
