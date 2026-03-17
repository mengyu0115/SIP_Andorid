package com.sip.client.presence;

/**
 * SIP 在线状态管理回调接口
 *
 * 用于通知应用层状态变更事件
 */
public interface PresenceCallback {

    /**
     * 状态发布成功
     *
     * @param status 当前发布的状态
     */
    void onPublishSuccess(PresenceStatus status);

    /**
     * 状态发布失败
     *
     * @param error 错误信息
     */
    void onPublishFailed(String error);

    /**
     * 订阅成功
     *
     * @param targetUri 订阅的目标 URI
     */
    void onSubscribeSuccess(String targetUri);

    /**
     * 订阅失败
     *
     * @param targetUri 订阅的目标 URI
     * @param error 错误信息
     */
    void onSubscribeFailed(String targetUri, String error);

    /**
     * 收到状态通知
     *
     * @param fromUri 来源 URI
     * @param status 对方的在线状态
     */
    void onNotifyReceived(String fromUri, PresenceStatus status);

    /**
     * 订阅被接受
     *
     * @param targetUri 订阅的目标 URI
     */
    void onSubscriptionAccepted(String targetUri);

    /**
     * 订阅被拒绝
     *
     * @param targetUri 订阅的目标 URI
     * @param reason 拒绝原因
     */
    void onSubscriptionRejected(String targetUri, String reason);
}
