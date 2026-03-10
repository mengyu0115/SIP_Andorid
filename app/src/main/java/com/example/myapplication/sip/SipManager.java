package com.example.myapplication.sip;

import android.util.Log;

/**
 * SIP 管理器 — 外观/门面（Facade）
 *
 * 保持原有的单例 + 简单 API，内部委托给三层架构：
 *   SipStack          — UDP 基础设施（收发 + 路由）
 *   SipRegisterHandler — REGISTER + Digest 认证 + 心跳保活
 *   SipMessageHandler  — MESSAGE 发送/接收 + 401 认证重试
 *
 * 外部调用方（LoginActivity、ChatActivity）无需关心内部分层，
 * 继续使用 SipManager.getInstance().register/sendMessage/... 即可。
 *
 * 对应 PC 端架构：
 *   SipManager.java (core)  → SipStack
 *   SipRegisterManager.java → SipRegisterHandler
 *   SipMessageManager.java  → SipMessageHandler
 *   SipCallManager.java     → 未来扩展 SipCallHandler
 */
public class SipManager {

    private static final String TAG = "SipManager";

    // SIP 服务器默认值（可被 register() 动态覆盖）
    public static final String DEFAULT_SIP_SERVER = "10.129.114.129";
    public static final int SIP_SERVER_PORT = 5060;

    // 当前实际使用的 SIP 服务器地址
    private String sipServer = DEFAULT_SIP_SERVER;

    // ===== 单例 =====
    private static volatile SipManager instance;

    public static SipManager getInstance() {
        if (instance == null) {
            synchronized (SipManager.class) {
                if (instance == null) {
                    instance = new SipManager();
                }
            }
        }
        return instance;
    }

    // ===== 内部组件 =====
    private final SipStack stack;
    private SipRegisterHandler registerHandler;
    private SipMessageHandler messageHandler;
    private SipCallHandler callHandler;

    private SipManager() {
        stack = SipStack.getInstance();
        registerHandler = null;
        messageHandler = null;
        callHandler = null;
    }

    // ===== 回调接口（保持向后兼容）=====

    private RegisterCallback pendingCallback;

    public interface RegisterCallback {
        void onRegisterSuccess();
        void onRegisterFailed(String reason);
        void onUnregisterSuccess();
    }

    public void setCallback(RegisterCallback callback) {
        this.pendingCallback = callback;
        if (registerHandler != null) {
            applyCallback(callback);
        }
    }

    private void applyCallback(RegisterCallback callback) {
        registerHandler.setCallback(new SipRegisterHandler.RegisterCallback() {
            @Override
            public void onRegisterSuccess() {
                if (callback != null) callback.onRegisterSuccess();
            }

            @Override
            public void onRegisterFailed(String reason) {
                if (callback != null) callback.onRegisterFailed(reason);
            }

            @Override
            public void onUnregisterSuccess() {
                if (callback != null) callback.onUnregisterSuccess();
            }
        });
    }

    // ===== 初始化 =====

    public synchronized void initialize(String localIp, int localPort) throws Exception {
        stack.initialize(localIp, localPort);
    }

    // ===== 注册 =====

    public void register(String username, String password, String domain) {
        // 动态设置 SIP 服务器地址（使用登录页输入的 serverIp）
        this.sipServer = (domain != null && !domain.isEmpty()) ? domain : DEFAULT_SIP_SERVER;
        Log.i(TAG, "SIP 服务器地址: " + sipServer + ":" + SIP_SERVER_PORT);

        // 延迟初始化 Handler（首次或 SIP 服务器地址变化时重建）
        ensureHandlers();

        // 设置 MESSAGE handler 的凭证（用于 401 重试）
        messageHandler.setCredentials(username, password);
        // 设置 CALL handler 的凭证（用于 INVITE 401 重试）
        callHandler.setCredentials(username, password);
        registerHandler.register(username, password);
    }

    /** 确保 Handler 已创建并注册到 SipStack */
    private void ensureHandlers() {
        if (registerHandler == null || messageHandler == null) {
            registerHandler = new SipRegisterHandler(stack, sipServer, SIP_SERVER_PORT);
            messageHandler = new SipMessageHandler(stack, sipServer, SIP_SERVER_PORT);
            callHandler = new SipCallHandler(stack, sipServer, SIP_SERVER_PORT);
            stack.setRegisterHandler(registerHandler);
            stack.setMessageHandler(messageHandler);
            stack.setCallHandler(callHandler);
            // 应用之前设置的回调
            if (pendingCallback != null) {
                applyCallback(pendingCallback);
            }
        }
    }

    // ===== 发送消息 =====

    public void sendMessage(String targetUsername, String content) {
        if (messageHandler == null) {
            Log.e(TAG, "sendMessage 失败: SIP 未初始化");
            return;
        }
        messageHandler.sendMessage(targetUsername, content);
    }

    // ===== 注销 =====

    public void unregister() {
        if (registerHandler == null) return;
        registerHandler.unregister();
    }

    // ===== 通话功能 =====

    public void makeCall(String targetUsername, String callType, int audioPort, int videoPort) {
        if (callHandler == null) {
            Log.e(TAG, "makeCall 失败: SIP 未初始化");
            return;
        }
        callHandler.makeCall(targetUsername, callType, audioPort, videoPort);
    }

    public void answerCall(int audioPort, int videoPort) {
        if (callHandler == null) return;
        callHandler.answerCall(audioPort, videoPort);
    }

    public void hangupCall() {
        if (callHandler == null) return;
        callHandler.hangup();
    }

    public void rejectCall() {
        if (callHandler == null) return;
        callHandler.rejectCall();
    }

    public SipCallHandler getCallHandler() { return callHandler; }

    /**
     * 设置全局来电监听器（供 MainActivity 注册，在收到 INVITE 时启动 CallActivity）
     */
    public void setIncomingCallListener(SipCallHandler.CallEventListener listener) {
        if (callHandler != null) {
            callHandler.setCallEventListener(listener);
        }
    }

    // ===== 关闭 =====

    public void shutdown() {
        if (callHandler != null) callHandler.hangup();
        if (registerHandler != null) registerHandler.shutdown();
        stack.shutdown();
    }

    // ===== 状态查询 =====

    public boolean isRegistered() {
        return registerHandler != null && registerHandler.isRegistered();
    }

    public String getLocalIp() { return stack.getLocalIp(); }
    public int getLocalPort() { return stack.getLocalPort(); }

    // ===== 直接暴露子组件（供高级用法）=====

    public SipStack getStack() { return stack; }
    public SipRegisterHandler getRegisterHandler() { return registerHandler; }
    public SipMessageHandler getMessageHandler() { return messageHandler; }
}
