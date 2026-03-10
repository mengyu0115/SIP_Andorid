package com.sip.client.core.listener;

/**
 * 通话状态监听器接口
 * 用于监听 SIP 通话状态的变化
 *
 * 功能:
 * 1. 呼叫状态变化通知
 * 2. 振铃通知
 * 3. 通话建立通知
 * 4. 通话结束通知
 * 5. 通话失败通知
 *
 * 设计模式: 观察者模式
 * 作用: 允许 UI 层或其他模块监听通话状态
 *
 * @author SIP 项目组
 * @version 1.0
 */
public interface CallStateListener {

    /**
     * 当呼叫状态改变时调用
     *
     * @param callId 呼叫 ID
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void onCallStateChanged(String callId, CallState oldState, CallState newState);

    /**
     * 当有来电时调用
     *
     * @param callId 呼叫 ID
     * @param callerUri 主叫方 URI
     * @param callType 通话类型 (audio/video)
     */
    void onIncomingCall(String callId, String callerUri, String callType);

    /**
     * 当对方振铃时调用
     *
     * @param callId 呼叫 ID
     */
    void onRinging(String callId);

    /**
     * 当通话建立时调用
     *
     * @param callId 呼叫 ID
     */
    void onCallEstablished(String callId);

    /**
     * 当通话结束时调用
     *
     * @param callId 呼叫 ID
     * @param reason 结束原因
     */
    void onCallEnded(String callId, String reason);

    /**
     * 当通话失败时调用
     *
     * @param callId 呼叫 ID
     * @param errorMessage 错误信息
     */
    void onCallFailed(String callId, String errorMessage);

    /**
     * 通话状态枚举
     */
    enum CallState {
        /**
         * 空闲状态
         */
        IDLE,

        /**
         * 呼叫中 (主叫方)
         */
        CALLING,

        /**
         * 振铃中 (被叫方)
         */
        RINGING,

        /**
         * 通话中
         */
        IN_CALL,

        /**
         * 结束中
         */
        ENDING,

        /**
         * 已结束
         */
        TERMINATED,

        /**
         * 失败
         */
        FAILED
    }
}
