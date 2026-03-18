package com.sip.client.core;

import com.sip.client.core.listener.SipEventListener;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * SIP 用户代理 (User Agent)
 * 提供 SIP 消息处理的基础实现
 *
 * 功能:
 * 1. 实现 SipListener 接口，处理 SIP 事件
 * 2. 提供请求/响应处理的模板方法
 * 3. 管理 SIP 事件监听器
 * 4. 提供工厂方法访问
 *
 * 设计模式: 模板方法模式
 * 作用: 作为具体 SIP 功能模块的基类
 *
 * @author SIP 项目组
 * @version 1.0
 */
@Slf4j
public abstract class SipUserAgent implements SipListener {

    // ========== SIP 核心组件 ==========
    protected SipManager sipManager;
    protected SipProvider sipProvider;
    protected AddressFactory addressFactory;
    protected HeaderFactory headerFactory;
    protected MessageFactory messageFactory;

    // ========== 事件监听器 ==========
    protected List<SipEventListener> eventListeners = new ArrayList<>();

    // ========== 用户信息 ==========
    protected String localUsername;
    protected String localDomain;
    protected String localIp;
    protected int localPort;

    /**
     * 初始化 SIP 用户代理
     *
     * @param sipManager SIP 管理器实例
     * @param username 用户名
     * @param domain 域名
     */
    public void initialize(SipManager sipManager, String username, String domain) throws Exception {
        this.sipManager = sipManager;
        this.localUsername = username;
        this.localDomain = domain;
        this.localIp = sipManager.getLocalIp();
        this.localPort = sipManager.getLocalPort();

        // 获取 SIP 组件
        this.sipProvider = sipManager.getSipProvider();
        this.addressFactory = sipManager.getAddressFactory();
        this.headerFactory = sipManager.getHeaderFactory();
        this.messageFactory = sipManager.getMessageFactory();

        // 注册为 SipListener
        this.sipProvider.addSipListener(this);

        log.info("SipUserAgent 初始化成功: {}@{}", username, domain);

        // 调用子类初始化
        onInitialize();
    }

    /**
     * 子类初始化回调 (模板方法)
     */
    protected abstract void onInitialize() throws Exception;

    // ========== SipListener 实现 ==========

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            String method = request.getMethod();

            log.debug("收到 SIP 请求: {}", method);

            // 通知监听器
            notifyRequestReceived(requestEvent);

            // 调用子类处理
            handleRequest(requestEvent);

        } catch (Exception e) {
            log.error("处理 SIP 请求失败", e);
            handleRequestError(requestEvent, e);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            int statusCode = response.getStatusCode();

            log.debug("收到 SIP 响应: {}", statusCode);

            // 通知监听器
            notifyResponseReceived(responseEvent);

            // 调用子类处理
            handleResponse(responseEvent);

        } catch (Exception e) {
            log.error("处理 SIP 响应失败", e);
            handleResponseError(responseEvent, e);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        try {
            log.warn("SIP 事务超时");

            // 通知监听器
            notifyTimeout(timeoutEvent);

            // 调用子类处理
            handleTimeout(timeoutEvent);

        } catch (Exception e) {
            log.error("处理超时事件失败", e);
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        try {
            log.error("SIP IO 异常: {}", exceptionEvent.getHost() + ":" + exceptionEvent.getPort());

            // 通知监听器
            notifyIOException(exceptionEvent);

            // 调用子类处理
            handleIOException(exceptionEvent);

        } catch (Exception e) {
            log.error("处理 IO 异常失败", e);
        }
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        try {
            log.debug("SIP 事务终止");

            // 调用子类处理
            handleTransactionTerminated(transactionTerminatedEvent);

        } catch (Exception e) {
            log.error("处理事务终止失败", e);
        }
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        try {
            log.debug("SIP Dialog 终止");

            // 调用子类处理
            handleDialogTerminated(dialogTerminatedEvent);

        } catch (Exception e) {
            log.error("处理 Dialog 终止失败", e);
        }
    }

    // ========== 抽象方法 (子类实现) ==========

    /**
     * 处理 SIP 请求 (子类实现)
     *
     * @param requestEvent 请求事件
     */
    protected abstract void handleRequest(RequestEvent requestEvent) throws Exception;

    /**
     * 处理 SIP 响应 (子类实现)
     *
     * @param responseEvent 响应事件
     */
    protected abstract void handleResponse(ResponseEvent responseEvent) throws Exception;

    /**
     * 处理超时事件 (子类实现)
     *
     * @param timeoutEvent 超时事件
     */
    protected abstract void handleTimeout(TimeoutEvent timeoutEvent) throws Exception;

    // ========== 可选覆盖的方法 (提供默认实现) ==========

    /**
     * 处理 IO 异常 (子类可覆盖)
     *
     * @param exceptionEvent IO 异常事件
     */
    protected void handleIOException(IOExceptionEvent exceptionEvent) {
        // 默认实现: 仅记录日志
        log.warn("IO 异常: {}", exceptionEvent);
    }

    /**
     * 处理事务终止 (子类可覆盖)
     *
     * @param event 事务终止事件
     */
    protected void handleTransactionTerminated(TransactionTerminatedEvent event) {
        // 默认实现: 无操作
    }

    /**
     * 处理 Dialog 终止 (子类可覆盖)
     *
     * @param event Dialog 终止事件
     */
    protected void handleDialogTerminated(DialogTerminatedEvent event) {
        // 默认实现: 无操作
    }

    /**
     * 处理请求错误 (子类可覆盖)
     *
     * @param requestEvent 请求事件
     * @param exception 异常
     */
    protected void handleRequestError(RequestEvent requestEvent, Exception exception) {
        log.error("请求处理错误: {}", exception.getMessage());
    }

    /**
     * 处理响应错误 (子类可覆盖)
     *
     * @param responseEvent 响应事件
     * @param exception 异常
     */
    protected void handleResponseError(ResponseEvent responseEvent, Exception exception) {
        log.error("响应处理错误: {}", exception.getMessage());
    }

    // ========== 监听器管理 ==========

    /**
     * 添加事件监听器
     *
     * @param listener 事件监听器
     */
    public void addEventListener(SipEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
            log.debug("添加事件监听器: {}", listener.getClass().getSimpleName());
        }
    }

    /**
     * 移除事件监听器
     *
     * @param listener 事件监听器
     */
    public void removeEventListener(SipEventListener listener) {
        eventListeners.remove(listener);
        log.debug("移除事件监听器: {}", listener.getClass().getSimpleName());
    }

    // ========== 事件通知 ==========

    /**
     * 通知收到请求
     */
    protected void notifyRequestReceived(RequestEvent requestEvent) {
        for (SipEventListener listener : eventListeners) {
            try {
                listener.onRequestReceived(requestEvent);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        }
    }

    /**
     * 通知收到响应
     */
    protected void notifyResponseReceived(ResponseEvent responseEvent) {
        for (SipEventListener listener : eventListeners) {
            try {
                listener.onResponseReceived(responseEvent);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        }
    }

    /**
     * 通知超时
     */
    protected void notifyTimeout(TimeoutEvent timeoutEvent) {
        for (SipEventListener listener : eventListeners) {
            try {
                listener.onTimeout(timeoutEvent);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        }
    }

    /**
     * 通知 IO 异常
     */
    protected void notifyIOException(IOExceptionEvent exceptionEvent) {
        for (SipEventListener listener : eventListeners) {
            try {
                listener.onIOException(exceptionEvent);
            } catch (Exception e) {
                log.error("监听器通知失败", e);
            }
        }
    }

    // ========== Getter 方法 ==========

    public String getLocalUsername() {
        return localUsername;
    }

    public String getLocalDomain() {
        return localDomain;
    }

    public String getLocalIp() {
        return localIp;
    }

    public int getLocalPort() {
        return localPort;
    }

    public SipProvider getSipProvider() {
        return sipProvider;
    }

    public AddressFactory getAddressFactory() {
        return addressFactory;
    }

    public HeaderFactory getHeaderFactory() {
        return headerFactory;
    }

    public MessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * 关闭用户代理
     */
    public void shutdown() {
        try {
            // 移除监听器
            if (sipProvider != null) {
                sipProvider.removeSipListener(this);
            }

            // 清空事件监听器
            eventListeners.clear();

            log.info("SipUserAgent 已关闭");

            // 调用子类清理
            onShutdown();

        } catch (Exception e) {
            log.error("关闭 SipUserAgent 失败", e);
        }
    }

    /**
     * 子类清理回调 (模板方法)
     */
    protected abstract void onShutdown();
}
