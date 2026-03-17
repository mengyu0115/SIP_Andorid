package com.example.myapplication;

import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SIP 消息接收全局单例（观察者模式）
 *
 * 对应 PC 端 SipMessageManager.messageCallback：
 * 当 SIP MESSAGE 到达时，通过此单例将消息路由到所有注册的 listener。
 *
 * 消息内容格式（与 PC 端 MainController 完全统一）：
 * - 文本：纯文本字符串
 * - 图片：[图片]<url>
 * - 文件：[文件]<filename>|<url>
 * - 语音：[语音]<url>
 * - 视频：[视频]<url>
 */
public class SipMessageReceiver {

    private static final String TAG = "SipMessageReceiver";

    public interface MessageCallback {
        /**
         * 收到新消息
         *
         * @param fromUsername 发送者用户名（从 SIP URI 提取，如 sip:alice@host -> alice）
         * @param content      消息内容
         */
        void onMessageReceived(String fromUsername, String content);
    }

    private static SipMessageReceiver instance;
    private final CopyOnWriteArrayList<MessageCallback> listeners = new CopyOnWriteArrayList<>();

    public static SipMessageReceiver getInstance() {
        if (instance == null) {
            synchronized (SipMessageReceiver.class) {
                if (instance == null) instance = new SipMessageReceiver();
            }
        }
        return instance;
    }

    private SipMessageReceiver() {}

    /** 添加消息监听器 */
    public void addListener(MessageCallback listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** 移除消息监听器 */
    public void removeListener(MessageCallback listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * @deprecated 使用 {@link #addListener} / {@link #removeListener} 替代。
     * 向后兼容：设置唯一的 callback（内部转为 add/remove）。
     */
    @Deprecated
    public void setCallback(MessageCallback callback) {
        // 移除旧的同类型 listener，添加新的
        // 为兼容旧代码，用标记接口包装
        listeners.removeIf(l -> l instanceof LegacyCallbackWrapper);
        if (callback != null) {
            listeners.add(new LegacyCallbackWrapper(callback));
        }
    }

    /**
     * 由 SIP 协议层调用（当收到 SIP MESSAGE 请求时）
     * 对应 PC 端 RegisterCallback.onMessageReceived
     *
     * @param fromSipUri SIP URI，如 sip:alice@10.129.114.129
     * @param content    消息内容
     */
    public void onSipMessageReceived(String fromSipUri, String content) {
        String fromUsername = extractUsername(fromSipUri);
        for (MessageCallback listener : listeners) {
            try {
                listener.onMessageReceived(fromUsername, content);
            } catch (Exception e) {
                Log.e(TAG, "listener 回调异常", e);
            }
        }
    }

    /**
     * 从 SIP URI 中提取用户名，并统一为纯数字 ID
     * 例如：sip:user101@10.29.209.85 -> 101
     *       sip:101@10.29.209.85     -> 101
     *
     * PC 端 SIP 账号格式为 "user101"，Android 端内部用纯数字 userId（"101"）
     * 作为 UnreadManager / ConversationFragment 的统一 key。
     * 这里在提取后去掉 "user" 前缀，保证两端 key 一致。
     */
    private String extractUsername(String sipUri) {
        if (sipUri == null) return "";
        String s = sipUri.startsWith("sip:") ? sipUri.substring(4) : sipUri;
        int at = s.indexOf('@');
        String username = at > 0 ? s.substring(0, at) : s;
        // 统一去掉 "user" 前缀，使 fromUsername 与 Android 内部 userId 字符串一致
        if (username.startsWith("user")) {
            username = username.substring(4);
        }
        return username;
    }

    /** 内部包装类，用于标记通过 setCallback 注册的旧回调 */
    private static class LegacyCallbackWrapper implements MessageCallback {
        private final MessageCallback delegate;

        LegacyCallbackWrapper(MessageCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessageReceived(String fromUsername, String content) {
            delegate.onMessageReceived(fromUsername, content);
        }
    }
}
