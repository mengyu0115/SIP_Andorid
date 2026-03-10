package com.sip.client.core.listener;

import javax.sip.*;

/**
 * SIP 事件监听器接口
 * 用于监听 SIP 协议栈的各种事件
 *
 * 功能:
 * 1. 请求接收事件
 * 2. 响应接收事件
 * 3. 超时事件
 * 4. IO 异常事件
 *
 * 设计模式: 观察者模式
 * 作用: 允许外部模块监听 SIP 事件
 *
 * @author SIP 项目组
 * @version 1.0
 */
public interface SipEventListener {

    /**
     * 当收到 SIP 请求时调用
     *
     * @param requestEvent 请求事件
     */
    void onRequestReceived(RequestEvent requestEvent);

    /**
     * 当收到 SIP 响应时调用
     *
     * @param responseEvent 响应事件
     */
    void onResponseReceived(ResponseEvent responseEvent);

    /**
     * 当 SIP 事务超时时调用
     *
     * @param timeoutEvent 超时事件
     */
    void onTimeout(TimeoutEvent timeoutEvent);

    /**
     * 当发生 IO 异常时调用
     *
     * @param exceptionEvent IO 异常事件
     */
    void onIOException(IOExceptionEvent exceptionEvent);
}
