package com.sip.client.presence;

import gov.nist.javax.sip.message.SIPResponse;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP 在线状态管理器
 *
 * 功能:
 * 1. SIP PUBLISH - 发布自己的在线状态
 * 2. SIP SUBSCRIBE - 订阅好友的在线状态
 * 3. SIP NOTIFY - 接收好友状态通知
 * 4. 定期刷新状态发布和订阅
 *
 * RFC 参考:
 * - RFC 3903: SIP Extension for Event State Publication (PUBLISH)
 * - RFC 6665: SIP-Specific Event Notification (SUBSCRIBE/NOTIFY)
 * - RFC 3863: Presence Information Data Format (PIDF)
 */
@Slf4j
public class PresenceManager implements SipListener {

    // ========== 单例模式 ==========
    private static volatile PresenceManager instance;

    public static PresenceManager getInstance() {
        if (instance == null) {
            synchronized (PresenceManager.class) {
                if (instance == null) {
                    instance = new PresenceManager();
                }
            }
        }
        return instance;
    }

    // ========== SIP 协议栈组件 ==========
    private SipStack sipStack;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    // ========== 用户信息 ==========
    private String username;
    private String domain;
    private String localIp;
    private int localPort;

    // ========== 在线状态 ==========
    private PresenceStatus currentStatus = PresenceStatus.OFFLINE;
    private Timer publishTimer;
    private int publishExpires = 3600; // PUBLISH 过期时间(秒)
    private String publishETag; // PUBLISH 实体标签

    // ========== 订阅管理 ==========
    private final Map<String, SubscriptionInfo> subscriptions = new ConcurrentHashMap<>();
    private Timer subscribeTimer;
    private int subscribeExpires = 3600; // SUBSCRIBE 过期时间(秒)

    // ========== 回调接口 ==========
    private PresenceCallback callback;

    // ========== Call-ID 管理 ==========
    private final Map<String, CallIdHeader> callIds = new ConcurrentHashMap<>();
    private final Map<String, Long> cseqs = new ConcurrentHashMap<>();

    private PresenceManager() {
    }

    /**
     * 订阅信息类
     */
    private static class SubscriptionInfo {
        String targetUri;
        CallIdHeader callId;
        long cseq;
        PresenceStatus lastStatus;
        String dialogId;

        SubscriptionInfo(String targetUri, CallIdHeader callId) {
            this.targetUri = targetUri;
            this.callId = callId;
            this.cseq = 1;
            this.lastStatus = PresenceStatus.OFFLINE;
        }
    }

    /**
     * 初始化 SIP 协议栈 (共用 SipRegisterManager 的协议栈)
     */
    public void initialize(SipStack sipStack, SipProvider sipProvider,
                          AddressFactory addressFactory, HeaderFactory headerFactory,
                          MessageFactory messageFactory, String username, String domain,
                          String localIp, int localPort) {
        log.info("初始化 SIP 在线状态管理器: {}@{}", username, domain);

        this.sipStack = sipStack;
        this.sipProvider = sipProvider;
        this.addressFactory = addressFactory;
        this.headerFactory = headerFactory;
        this.messageFactory = messageFactory;
        this.username = username;
        this.domain = domain;
        this.localIp = localIp;
        this.localPort = localPort;

        // 添加 SIP 监听器
        try {
            sipProvider.addSipListener(this);
            log.info("SIP 在线状态管理器初始化成功");
        } catch (TooManyListenersException e) {
            log.error("添加 SIP 监听器失败", e);
        }
    }

    /**
     * 设置回调接口
     */
    public void setCallback(PresenceCallback callback) {
        this.callback = callback;
    }

    // ========================================
    // PUBLISH 功能实现
    // ========================================

    /**
     * 发布在线状态
     *
     * @param status 要发布的状态
     */
    public void publishStatus(PresenceStatus status) {
        try {
            this.currentStatus = status;
            log.info("发布在线状态: {}", status.getDisplayName());

            sendPublish(status);

        } catch (Exception e) {
            log.error("发布状态失败", e);
            if (callback != null) {
                callback.onPublishFailed("发布失败: " + e.getMessage());
            }
        }
    }

    /**
     * 发送 PUBLISH 请求
     */
    private void sendPublish(PresenceStatus status) throws ParseException,
            InvalidArgumentException, SipException {

        // 1. 创建 Request URI
        SipURI requestURI = addressFactory.createSipURI(username, domain);

        // 2. 创建 From 头
        SipURI fromURI = addressFactory.createSipURI(username, domain);
        Address fromAddress = addressFactory.createAddress(fromURI);
        fromAddress.setDisplayName(username);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress,
                String.valueOf(System.currentTimeMillis()));

        // 3. 创建 To 头
        SipURI toURI = addressFactory.createSipURI(username, domain);
        Address toAddress = addressFactory.createAddress(toURI);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // 4. 创建 Via 头
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
        viaHeaders.add(viaHeader);

        // 5. 创建或获取 Call-ID 头
        CallIdHeader callIdHeader = callIds.get("publish");
        if (callIdHeader == null) {
            callIdHeader = sipProvider.getNewCallId();
            callIds.put("publish", callIdHeader);
            cseqs.put("publish", 1L);
        }

        // 6. 创建 CSeq 头
        long cseq = cseqs.get("publish");
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.PUBLISH);
        cseqs.put("publish", cseq + 1);

        // 7. 创建 Max-Forwards 头
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // 8. 创建 PUBLISH 请求
        Request request = messageFactory.createRequest(requestURI, Request.PUBLISH,
                callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

        // 9. 添加 Event 头 (presence)
        EventHeader eventHeader = headerFactory.createEventHeader("presence");
        request.addHeader(eventHeader);

        // 10. 添加 Expires 头
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(publishExpires);
        request.addHeader(expiresHeader);

        // 11. 添加 SIP-If-Match 头 (如果是刷新)
        if (publishETag != null) {
            SIPIfMatchHeader ifMatchHeader = headerFactory.createSIPIfMatchHeader(publishETag);
            request.addHeader(ifMatchHeader);
        }

        // 12. 添加 Content-Type 头
        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(
                "application", "pidf+xml");
        request.addHeader(contentTypeHeader);

        // 13. 创建 PIDF XML 内容
        String pidfBody = createPidfXml(status);
        request.setContent(pidfBody, contentTypeHeader);

        // 14. 发送请求
        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
        clientTransaction.sendRequest();

        log.info("PUBLISH 请求已发送: {}", status.getDisplayName());
    }

    /**
     * 创建 PIDF XML 内容
     *
     * 示例:
     * <?xml version="1.0" encoding="UTF-8"?>
     * <presence xmlns="urn:ietf:params:xml:ns:pidf" entity="sip:user@domain">
     *   <tuple id="tuple-1">
     *     <status><basic>open</basic></status>
     *     <contact>sip:user@192.168.1.100:5060</contact>
     *   </tuple>
     * </presence>
     */
    private String createPidfXml(PresenceStatus status) {
        String entity = "sip:" + username + "@" + domain;
        String contact = "sip:" + username + "@" + localIp + ":" + localPort;

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\" entity=\"" + entity + "\">\n" +
                "  <tuple id=\"tuple-1\">\n" +
                "    <status><basic>" + status.getPidfStatus() + "</basic></status>\n" +
                "    <contact>" + contact + "</contact>\n" +
                "  </tuple>\n" +
                "</presence>";
    }

    /**
     * 启动 PUBLISH 定期刷新
     */
    private void startPublishKeepAlive() {
        if (publishTimer != null) {
            publishTimer.cancel();
        }

        publishTimer = new Timer("PublishKeepAlive");
        long refreshInterval = (publishExpires - 60) * 1000L; // 提前 60 秒刷新

        publishTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    log.info("执行 PUBLISH 心跳刷新");
                    sendPublish(currentStatus);
                } catch (Exception e) {
                    log.error("PUBLISH 心跳刷新失败", e);
                }
            }
        }, refreshInterval, refreshInterval);

        log.info("PUBLISH 心跳保活已启动,刷新间隔: {} 秒", publishExpires - 60);
    }

    /**
     * 停止 PUBLISH 定期刷新
     */
    private void stopPublishKeepAlive() {
        if (publishTimer != null) {
            publishTimer.cancel();
            publishTimer = null;
            log.info("PUBLISH 心跳保活已停止");
        }
    }

    // ========================================
    // SUBSCRIBE 功能实现
    // ========================================

    /**
     * 订阅好友的在线状态
     *
     * @param targetUri 好友的 SIP URI (如: user2@domain)
     */
    public void subscribe(String targetUri) {
        try {
            log.info("订阅好友状态: {}", targetUri);

            sendSubscribe(targetUri);

        } catch (Exception e) {
            log.error("订阅失败", e);
            if (callback != null) {
                callback.onSubscribeFailed(targetUri, "订阅失败: " + e.getMessage());
            }
        }
    }

    /**
     * 发送 SUBSCRIBE 请求
     */
    private void sendSubscribe(String targetUri) throws ParseException,
            InvalidArgumentException, SipException {

        // 解析目标 URI
        String[] parts = targetUri.split("@");
        String targetUser = parts[0];
        String targetDomain = parts.length > 1 ? parts[1] : domain;

        // 1. 创建 Request URI
        SipURI requestURI = addressFactory.createSipURI(targetUser, targetDomain);

        // 2. 创建 From 头
        SipURI fromURI = addressFactory.createSipURI(username, domain);
        Address fromAddress = addressFactory.createAddress(fromURI);
        fromAddress.setDisplayName(username);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress,
                String.valueOf(System.currentTimeMillis()));

        // 3. 创建 To 头
        SipURI toURI = addressFactory.createSipURI(targetUser, targetDomain);
        Address toAddress = addressFactory.createAddress(toURI);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // 4. 创建 Via 头
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
        viaHeaders.add(viaHeader);

        // 5. 创建或获取 Call-ID 头
        CallIdHeader callIdHeader = callIds.get("subscribe-" + targetUri);
        if (callIdHeader == null) {
            callIdHeader = sipProvider.getNewCallId();
            callIds.put("subscribe-" + targetUri, callIdHeader);
            cseqs.put("subscribe-" + targetUri, 1L);
        }

        // 6. 创建 CSeq 头
        long cseq = cseqs.getOrDefault("subscribe-" + targetUri, 1L);
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.SUBSCRIBE);
        cseqs.put("subscribe-" + targetUri, cseq + 1);

        // 7. 创建 Max-Forwards 头
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // 8. 创建 SUBSCRIBE 请求
        Request request = messageFactory.createRequest(requestURI, Request.SUBSCRIBE,
                callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

        // 9. 添加 Event 头 (presence)
        EventHeader eventHeader = headerFactory.createEventHeader("presence");
        request.addHeader(eventHeader);

        // 10. 添加 Expires 头
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(subscribeExpires);
        request.addHeader(expiresHeader);

        // 11. 添加 Accept 头
        AcceptHeader acceptHeader = headerFactory.createAcceptHeader("application", "pidf+xml");
        request.addHeader(acceptHeader);

        // 12. 添加 Contact 头
        SipURI contactURI = addressFactory.createSipURI(username, localIp);
        contactURI.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactURI);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        request.addHeader(contactHeader);

        // 13. 发送请求
        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
        clientTransaction.sendRequest();

        // 14. 保存订阅信息
        SubscriptionInfo subInfo = new SubscriptionInfo(targetUri, callIdHeader);
        subscriptions.put(targetUri, subInfo);

        log.info("SUBSCRIBE 请求已发送: {}", targetUri);
    }

    /**
     * 取消订阅
     *
     * @param targetUri 好友的 SIP URI
     */
    public void unsubscribe(String targetUri) {
        try {
            log.info("取消订阅: {}", targetUri);

            // 发送 Expires=0 的 SUBSCRIBE 来取消订阅
            sendUnsubscribe(targetUri);

            // 移除订阅信息
            subscriptions.remove(targetUri);
            callIds.remove("subscribe-" + targetUri);
            cseqs.remove("subscribe-" + targetUri);

        } catch (Exception e) {
            log.error("取消订阅失败", e);
        }
    }

    /**
     * 发送取消订阅请求
     */
    private void sendUnsubscribe(String targetUri) throws ParseException,
            InvalidArgumentException, SipException {
        // 类似 sendSubscribe,但 Expires=0
        // 实现逻辑省略...
    }

    /**
     * 启动 SUBSCRIBE 定期刷新
     */
    private void startSubscribeKeepAlive() {
        if (subscribeTimer != null) {
            subscribeTimer.cancel();
        }

        subscribeTimer = new Timer("SubscribeKeepAlive");
        long refreshInterval = (subscribeExpires - 60) * 1000L; // 提前 60 秒刷新

        subscribeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    log.info("执行 SUBSCRIBE 心跳刷新");
                    for (String targetUri : subscriptions.keySet()) {
                        sendSubscribe(targetUri);
                    }
                } catch (Exception e) {
                    log.error("SUBSCRIBE 心跳刷新失败", e);
                }
            }
        }, refreshInterval, refreshInterval);

        log.info("SUBSCRIBE 心跳保活已启动,刷新间隔: {} 秒", subscribeExpires - 60);
    }

    /**
     * 停止 SUBSCRIBE 定期刷新
     */
    private void stopSubscribeKeepAlive() {
        if (subscribeTimer != null) {
            subscribeTimer.cancel();
            subscribeTimer = null;
            log.info("SUBSCRIBE 心跳保活已停止");
        }
    }

    // ========================================
    // SIP 事件处理
    // ========================================

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        int statusCode = response.getStatusCode();
        String method = cSeqHeader.getMethod();

        log.info("收到响应: {} - {}", statusCode, method);

        if (method.equals(Request.PUBLISH)) {
            handlePublishResponse(response, statusCode);
        } else if (method.equals(Request.SUBSCRIBE)) {
            handleSubscribeResponse(response, statusCode);
        }
    }

    /**
     * 处理 PUBLISH 响应
     */
    private void handlePublishResponse(Response response, int statusCode) {
        if (statusCode == Response.OK) {
            // 200 OK - 发布成功
            SIPETagHeader eTagHeader = (SIPETagHeader) response.getHeader("SIP-ETag");
            if (eTagHeader != null) {
                publishETag = eTagHeader.getETag();
            }

            log.info("状态发布成功: {}", currentStatus.getDisplayName());

            if (callback != null) {
                callback.onPublishSuccess(currentStatus);
            }

            // 启动心跳保活
            startPublishKeepAlive();

        } else {
            log.error("状态发布失败: {}", statusCode);
            if (callback != null) {
                callback.onPublishFailed("发布失败: " + statusCode);
            }
        }
    }

    /**
     * 处理 SUBSCRIBE 响应
     */
    private void handleSubscribeResponse(Response response, int statusCode) {
        // 从响应中获取目标 URI
        ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
        String targetUri = toHeader.getAddress().getURI().toString();
        targetUri = targetUri.replace("sip:", "");

        if (statusCode == Response.OK || statusCode == Response.ACCEPTED) {
            // 200 OK / 202 Accepted - 订阅成功
            log.info("订阅成功: {}", targetUri);

            if (callback != null) {
                callback.onSubscribeSuccess(targetUri);
                callback.onSubscriptionAccepted(targetUri);
            }

            // 启动心跳保活
            if (subscribeTimer == null) {
                startSubscribeKeepAlive();
            }

        } else if (statusCode == Response.FORBIDDEN) {
            // 403 Forbidden - 订阅被拒绝
            log.warn("订阅被拒绝: {}", targetUri);
            if (callback != null) {
                callback.onSubscriptionRejected(targetUri, "订阅被拒绝");
            }

        } else {
            log.error("订阅失败: {} - {}", targetUri, statusCode);
            if (callback != null) {
                callback.onSubscribeFailed(targetUri, "订阅失败: " + statusCode);
            }
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();

        log.info("收到请求: {}", method);

        if (method.equals(Request.NOTIFY)) {
            handleNotifyRequest(requestEvent);
        }
    }

    /**
     * 处理 NOTIFY 请求 (接收好友状态通知)
     */
    private void handleNotifyRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();

            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }

            // 1. 解析 From 头获取发送者 URI
            FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
            String fromUri = fromHeader.getAddress().getURI().toString();
            fromUri = fromUri.replace("sip:", "");

            log.info("收到 NOTIFY 请求,来自: {}", fromUri);

            // 2. 解析 PIDF XML 内容获取状态
            byte[] content = request.getRawContent();
            if (content != null && content.length > 0) {
                String pidfXml = new String(content);
                PresenceStatus status = parsePidfXml(pidfXml);

                log.info("好友 {} 的状态: {}", fromUri, status.getDisplayName());

                // 3. 更新订阅信息中的状态
                SubscriptionInfo subInfo = subscriptions.get(fromUri);
                if (subInfo != null) {
                    subInfo.lastStatus = status;
                }

                // 4. 回调通知应用层
                if (callback != null) {
                    callback.onNotifyReceived(fromUri, status);
                }
            }

            // 5. 发送 200 OK 响应
            Response response = messageFactory.createResponse(Response.OK, request);
            serverTransaction.sendResponse(response);

            log.info("NOTIFY 响应已发送: 200 OK");

        } catch (Exception e) {
            log.error("处理 NOTIFY 请求失败", e);
        }
    }

    /**
     * 解析 PIDF XML 内容获取状态
     */
    private PresenceStatus parsePidfXml(String pidfXml) {
        try {
            // 简单的 XML 解析 (生产环境建议使用 XML 解析库)
            if (pidfXml.contains("<basic>open</basic>")) {
                return PresenceStatus.ONLINE;
            } else if (pidfXml.contains("<basic>closed</basic>")) {
                return PresenceStatus.OFFLINE;
            } else if (pidfXml.contains("<basic>busy</basic>")) {
                return PresenceStatus.BUSY;
            } else if (pidfXml.contains("<basic>away</basic>")) {
                return PresenceStatus.AWAY;
            }
        } catch (Exception e) {
            log.error("解析 PIDF XML 失败", e);
        }
        return PresenceStatus.OFFLINE;
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP 请求超时");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("SIP IO 异常: {}", exceptionEvent.getHost());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        log.debug("SIP 事务终止");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        log.debug("SIP 对话终止");
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        log.info("关闭 SIP 在线状态管理器");

        stopPublishKeepAlive();
        stopSubscribeKeepAlive();

        // 取消所有订阅
        for (String targetUri : new ArrayList<>(subscriptions.keySet())) {
            unsubscribe(targetUri);
        }

        subscriptions.clear();
        callIds.clear();
        cseqs.clear();
    }

    /**
     * 获取当前状态
     */
    public PresenceStatus getCurrentStatus() {
        return currentStatus;
    }

    /**
     * 获取订阅的好友列表
     */
    public Set<String> getSubscribedUsers() {
        return subscriptions.keySet();
    }

    /**
     * 获取好友的最后已知状态
     */
    public PresenceStatus getFriendStatus(String targetUri) {
        SubscriptionInfo subInfo = subscriptions.get(targetUri);
        return subInfo != null ? subInfo.lastStatus : PresenceStatus.OFFLINE;
    }
}
