package com.sip.client.register;

import gov.nist.javax.sip.message.SIPRequest;
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
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SIP 注册管理器 - 合并版本 (成员4基础 + MSS掉线修复)
 * 负责处理 SIP REGISTER 注册、认证、心跳保活等功能
 *
 * 功能:
 * 1. SIP REGISTER 请求发送
 * 2. Digest 认证处理 (401/407 响应)
 * 3. 注册保活 (定期 Re-REGISTER) - ✅ 修复MSS 3分钟掉线问题
 * 4. 注销功能
 * 5. MESSAGE请求处理 (支持消息接收)
 *
 * 修复说明:
 * - ✅ 从200 OK响应中读取服务器返回的实际Expires值
 * - ✅ 如果服务器返回的Expires小于客户端设置,更新为服务器值
 * - ✅ 支持MESSAGE请求处理,无需额外监听器
 * - ✅ 提供公共getter方法供其他模块使用
 *
 * @author 成员1,3,4合并版本
 * @version 2.0 - 合并修复版
 */
@Slf4j
public class SipRegisterManager implements SipListener {

    // ========== 单例模式 ==========
    private static volatile SipRegisterManager instance;

    public static SipRegisterManager getInstance() {
        if (instance == null) {
            synchronized (SipRegisterManager.class) {
                if (instance == null) {
                    instance = new SipRegisterManager();
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
    private String password;
    private String domain;
    private String serverHost;  // SIP服务器地址
    private int serverPort;      // SIP服务器端口
    private String localIp;
    private int localPort;

    // ========== 注册状态 ==========
    private AtomicBoolean registered = new AtomicBoolean(false);
    private CallIdHeader callIdHeader;
    private long cseq = 1;
    private Timer keepAliveTimer;
    private int expiresTime = 3600; // 注册过期时间(秒)

    // ========== 认证相关 ==========
    private SipAuthHandler authHandler;

    // ========== 回调接口 ==========
    private RegisterCallback callback;

    // ========== MESSAGE模块引用 ==========
    private com.sip.client.message.SipMessageManager messageManager;

    // ========== CALL模块引用 ==========
    private com.sip.client.call.SipCallManager callManager;

    private SipRegisterManager() {
        this.authHandler = new SipAuthHandler();
    }

    /**
     * 初始化 SIP 协议栈
     */
    public void initialize(String localIp, int localPort) throws Exception {
        log.info("初始化 SIP 注册管理器: {}:{}", localIp, localPort);

        // 如果 SIP 栈已经初始化过，就直接返回
        if (sipStack != null && sipProvider != null) {
            log.warn("SIP 栈和Provider已经存在，跳过初始化");
            return;
        }

        this.localIp = localIp;
        this.localPort = localPort;

        // 创建 SIP 工厂
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("javax.sip.STACK_NAME", "SipRegisterClient");
        properties.setProperty("javax.sip.IP_ADDRESS", localIp);
        // ✅ 优化：减少调试日志以加快初始化速度（16=ERROR级别，保留错误信息）
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");  // 16=ERROR（原32=DEBUG）
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "registerDebug.txt");  // 保留错误日志文件
        // properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "registerLog.txt");   // 不需要server日志

        // 创建 SIP 协议栈
        sipStack = sipFactory.createSipStack(properties);
        log.info("SIP 协议栈创建成功");

        // 创建工厂
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();

        // 创建监听点
        ListeningPoint listeningPoint = sipStack.createListeningPoint(localIp, localPort, "udp");

        // 只在 sipProvider 为空时创建
        if (sipProvider == null) {
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
        }

        log.info("SIP Provider 创建成功,监听 {}:{}", localIp, localPort);
    }


    /**
     * 执行 SIP 注册
     *
     * @param username SIP 用户名
     * @param password SIP 密码
     * @param domain SIP 域名 (如: myvoipapp.com)
     * @param serverHost SIP 服务器地址
     * @param serverPort SIP 服务器端口
     */
    public void register(String username, String password, String domain, String serverHost, int serverPort) {
        try {
            this.username = username;
            this.password = password;
            this.domain = domain;
            this.serverHost = serverHost;
            this.serverPort = serverPort;

            log.info("开始 SIP 注册: {}@{} (服务器: {}:{})", username, domain, serverHost, serverPort);

            // 发送初始 REGISTER 请求
            sendRegister(null);

        } catch (Exception e) {
            log.error("SIP 注册失败", e);
            if (callback != null) {
                callback.onRegisterFailed("注册失败: " + e.getMessage());
            }
        }
    }

    /**
     * 执行 SIP 注册 (使用默认服务器地址)
     *
     * @param username SIP 用户名
     * @param password SIP 密码
     * @param domain SIP 域名 (如: myvoipapp.com)
     */
    public void register(String username, String password, String domain) {
        // 从domain解析服务器地址（简单实现，直接使用domain作为服务器地址）
        register(username, password, domain,"10.29.209.85" , 5060);
    }

    /**
     * 发送 REGISTER 请求
     *
     * @param authorizationHeader 认证头(首次注册时为 null)
     */
    private void sendRegister(AuthorizationHeader authorizationHeader) throws ParseException,
            InvalidArgumentException, SipException {

        // 1. 创建 Request URI
        SipURI requestURI = addressFactory.createSipURI(null, serverHost);
        requestURI.setPort(serverPort);


        // 2. 创建 From 头
        SipURI fromURI = addressFactory.createSipURI(username, domain);
        Address fromAddress = addressFactory.createAddress(fromURI);
        fromAddress.setDisplayName(username);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, String.valueOf(System.currentTimeMillis()));

        // 3. 创建 To 头
        SipURI toURI = addressFactory.createSipURI(username, domain);
        Address toAddress = addressFactory.createAddress(toURI);
        toAddress.setDisplayName(username);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // 4. 创建 Via 头
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
        viaHeaders.add(viaHeader);

        // 5. 创建 Call-ID 头
        if (callIdHeader == null) {
            callIdHeader = sipProvider.getNewCallId();
        }

        // 6. 创建 CSeq 头
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq++, Request.REGISTER);

        // 7. 创建 Max-Forwards 头
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // 8. 创建 REGISTER 请求
        Request request = messageFactory.createRequest(requestURI, Request.REGISTER,
                callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

        // 9. 添加 Contact 头
        SipURI contactURI = addressFactory.createSipURI(username, localIp);
        contactURI.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactURI);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        request.addHeader(contactHeader);

        // 10. 添加 Expires 头
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(expiresTime);
        request.addHeader(expiresHeader);

        // 11. 添加认证头(如果有)
        if (authorizationHeader != null) {
            request.addHeader(authorizationHeader);
        }

        // 12. 添加 Route 头(指定服务器地址)
        if (serverHost != null && serverPort > 0) {
            SipURI routeURI = addressFactory.createSipURI(null, serverHost);
            routeURI.setPort(serverPort);
            routeURI.setLrParam(); // 设置lr参数（loose routing）
            Address routeAddress = addressFactory.createAddress(routeURI);
            RouteHeader routeHeader = headerFactory.createRouteHeader(routeAddress);
            request.addHeader(routeHeader);
            log.debug("添加 Route 头: sip:{}:{};lr", serverHost, serverPort);
        }

        // 13. 发送请求
        ClientTransaction transaction = sipProvider.getNewClientTransaction(request);
        transaction.sendRequest();

        log.info("发送 REGISTER 请求");
        log.debug("Request URI: {}", requestURI);
        log.debug("From: {}@{}", username, domain);
        log.debug("Contact: {}@{}:{}", username, localIp, localPort);
    }

    /**
     * 注销
     */
    public void unregister() {
        try {
            if (!registered.get()) {
                log.warn("用户未注册,无需注销");
                return;
            }

            log.info("开始注销: {}@{}", username, domain);

            // 停止心跳
            stopKeepAlive();

            // 发送 Expires=0 的 REGISTER 请求
            expiresTime = 0;
            sendRegister(null);

            registered.set(false);

        } catch (Exception e) {
            log.error("注销失败", e);
        }
    }

    /**
     * 启动心跳保活
     */
    private void startKeepAlive() {
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
        }

        keepAliveTimer = new Timer("SIP-KeepAlive", true);
        // 提前 60 秒重新注册
        long period = (expiresTime - 60) * 1000L;

        keepAliveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    log.info("执行心跳保活注册");
                    sendRegister(null);
                } catch (Exception e) {
                    log.error("心跳保活失败", e);
                }
            }
        }, period, period);

        log.info("心跳保活已启动,周期: {} 秒", expiresTime - 60);
    }

    /**
     * 停止心跳保活
     */
    private void stopKeepAlive() {
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
            keepAliveTimer = null;
            log.info("心跳保活已停止");
        }
    }

    /**
     * 关闭 SIP 协议栈
     */
    public void shutdown() {
        try {
            stopKeepAlive();
            if (registered.get()) {
                unregister();
            }
            if (sipProvider != null) {
                sipProvider.removeSipListener(this);
                sipStack.deleteSipProvider(sipProvider);
            }
            if (sipStack != null) {
                sipStack.stop();
            }
            log.info("SIP 注册管理器已关闭");
        } catch (Exception e) {
            log.error("关闭 SIP 协议栈失败", e);
        }
    }

    // ========== SipListener 回调方法 ==========

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();

        log.info("收到响应: {} {}", statusCode, response.getReasonPhrase());

        try {
            // ✅ 检查CSeq方法，区分不同类型的响应
            CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            String method = cseqHeader.getMethod();

            if (Request.MESSAGE.equals(method)) {
                // MESSAGE响应：转发给SipMessageManager处理
                log.info("⏩ 检测到MESSAGE响应，转发给SipMessageManager处理");
                if (messageManager != null) {
                    messageManager.handleResponseEvent(responseEvent);
                } else {
                    log.warn("⚠️ MessageManager未设置，无法处理MESSAGE响应");
                }
                return;  // 不再继续处理
            }

            // ✅ INVITE/BYE/ACK/CANCEL响应：转发给CallManager处理
            if (Request.INVITE.equals(method) || Request.BYE.equals(method) ||
                Request.ACK.equals(method) || Request.CANCEL.equals(method)) {
                log.info("⏩ 检测到{}响应，转发给CallManager处理", method);
                if (callManager != null) {
                    callManager.processResponse(responseEvent);
                } else {
                    log.warn("⚠️ CallManager未设置，无法处理{}响应", method);
                }
                return;  // 不再继续处理
            }

            // REGISTER响应：按原有逻辑处理
            if (statusCode == Response.OK) {
                // 注册成功
                handleRegisterSuccess(response);

            } else if (statusCode == Response.UNAUTHORIZED || statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {
                // 需要认证
                handleAuthChallenge(responseEvent, statusCode);

            } else {
                // 注册失败
                log.error("注册失败: {} {}", statusCode, response.getReasonPhrase());
                if (callback != null) {
                    callback.onRegisterFailed("注册失败: " + statusCode + " " + response.getReasonPhrase());
                }
            }
        } catch (Exception e) {
            log.error("处理响应失败", e);
        }
    }

    /**
     * 处理注册成功 - ✅ 修复MSS 3分钟掉线问题
     */
    private void handleRegisterSuccess(Response response) {
        if (expiresTime == 0) {
            // 注销成功
            registered.set(false);
            log.info("注销成功");
            if (callback != null) {
                callback.onUnregisterSuccess();
            }
        } else {
            // 注册成功
            registered.set(true);

            // ✅ 【修复MSS掉线】从200 OK响应中读取服务器返回的实际Expires值
            try {
                int serverExpires = -1;

                // 1. 优先读取独立的Expires头（MSS服务器使用这种方式）
                ExpiresHeader expiresHeader = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
                if (expiresHeader != null) {
                    serverExpires = expiresHeader.getExpires();
                    log.info("从Expires头读取到服务器返回值: {}秒", serverExpires);
                }

                // 2. 如果没有Expires头，尝试从Contact头的expires参数读取
                if (serverExpires <= 0) {
                    ContactHeader contactHeader = (ContactHeader) response.getHeader(ContactHeader.NAME);
                    if (contactHeader != null) {
                        serverExpires = contactHeader.getExpires();
                        if (serverExpires > 0) {
                            log.info("从Contact头expires参数读取到服务器返回值: {}秒", serverExpires);
                        }
                    }
                }

                // 3. 使用服务器返回的值更新客户端的expiresTime
                if (serverExpires > 0 && serverExpires != expiresTime) {
                    log.warn("⚠️ MSS服务器返回的Expires({})与客户端设置({})不同, 更新为服务器值以避免掉线",
                             serverExpires, expiresTime);
                    this.expiresTime = serverExpires;
                } else if (serverExpires > 0) {
                    log.info("服务器接受客户端Expires: {}秒", expiresTime);
                } else {
                    log.warn("未能从200 OK响应中读取Expires值,使用客户端默认值: {}秒", expiresTime);
                }
            } catch (Exception e) {
                log.warn("读取服务器Expires失败,使用客户端默认值: {}秒", expiresTime, e);
            }

            log.info("注册成功: {}@{}, Expires={}秒", username, domain, expiresTime);

            // 启动心跳
            startKeepAlive();

            if (callback != null) {
                callback.onRegisterSuccess();
            }
        }
    }

    /**
     * 处理 401/407 认证质询
     */
    private void handleAuthChallenge(ResponseEvent responseEvent, int statusCode) throws Exception {
        Response response = responseEvent.getResponse();
        ClientTransaction transaction = responseEvent.getClientTransaction();
        Request request = transaction.getRequest();

        log.info("收到认证质询,开始生成认证响应");

        // 获取 WWW-Authenticate 或 Proxy-Authenticate 头
        WWWAuthenticateHeader wwwAuthHeader = null;
        ProxyAuthenticateHeader proxyAuthHeader = null;

        if (statusCode == Response.UNAUTHORIZED) {
            wwwAuthHeader = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
        } else {
            proxyAuthHeader = (ProxyAuthenticateHeader) response.getHeader(ProxyAuthenticateHeader.NAME);
        }

        // 生成认证头
        AuthorizationHeader authHeader;
        if (wwwAuthHeader != null) {
            authHeader = authHandler.generateAuthHeader(
                    request, wwwAuthHeader, username, password, AuthorizationHeader.NAME);
        } else {
            authHeader = authHandler.generateAuthHeader(
                    request, proxyAuthHeader, username, password, ProxyAuthorizationHeader.NAME);
        }

        // 重新发送带认证的 REGISTER 请求
        sendRegister(authHeader);
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();

        log.info("收到SIP请求: {}", method);

        try {
            if (Request.MESSAGE.equals(method)) {
                // 处理 MESSAGE 请求
                handleMessageRequest(requestEvent);
            } else if (Request.INVITE.equals(method) || Request.BYE.equals(method) ||
                       Request.ACK.equals(method) || Request.CANCEL.equals(method)) {
                // ✅ 转发呼叫相关请求给 CallManager
                if (callManager != null) {
                    log.info("转发{}请求给CallManager", method);
                    callManager.processRequest(requestEvent);
                } else {
                    log.warn("收到{}请求，但CallManager未初始化", method);
                    // 发送 503 Service Unavailable
                    Response response = messageFactory.createResponse(Response.SERVICE_UNAVAILABLE, request);
                    ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                    if (serverTransaction == null) {
                        serverTransaction = sipProvider.getNewServerTransaction(request);
                    }
                    serverTransaction.sendResponse(response);
                }
            } else {
                // 其他请求：发送 200 OK 响应
                Response response = messageFactory.createResponse(Response.OK, request);
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = sipProvider.getNewServerTransaction(request);
                }
                serverTransaction.sendResponse(response);
                log.info("已回复 200 OK: {}", method);
            }
        } catch (Exception e) {
            log.error("处理SIP请求失败", e);
        }
    }

    /**
     * 处理 MESSAGE 请求 - 支持消息模块接收
     */
    private void handleMessageRequest(RequestEvent requestEvent) {
        try {
            log.info("====================================================");
            log.info("📨 收到SIP MESSAGE请求");
            log.info("====================================================");

            Request request = requestEvent.getRequest();

            // 解析发送者
            FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
            Address fromAddress = fromHeader.getAddress();
            String from = fromAddress.getURI().toString();

            log.info("📨 原始发送者URI: {}", from);

            // 提取发送者用户名（去掉 sip: 前缀和域名）
            if (from.startsWith("sip:")) {
                from = from.substring(4);
                int atIndex = from.indexOf('@');
                if (atIndex > 0) {
                    from = from.substring(0, atIndex);
                }
            }

            log.info("📨 提取的发送者用户名: {}", from);

            // 获取消息内容
            String messageBody = new String(request.getRawContent(), "UTF-8");

            log.info("📨 消息内容: {}", messageBody);

            // 发送 200 OK 响应
            Response response = messageFactory.createResponse(Response.OK, request);
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }
            serverTransaction.sendResponse(response);
            log.info("✅ 已发送200 OK响应");

            // 通过回调通知应用层
            if (callback != null) {
                log.info("📞 准备调用callback.onMessageReceived()");
                callback.onMessageReceived(from, messageBody);
                log.info("✅ callback.onMessageReceived() 调用完成");
            } else {
                log.error("❌ callback 为 null，无法处理消息！");
            }

            log.info("====================================================");

        } catch (Exception e) {
            log.error("====================================================");
            log.error("❌ 处理MESSAGE请求失败", e);
            log.error("====================================================");
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.error("请求超时");
        if (callback != null) {
            callback.onRegisterFailed("请求超时");
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("IO异常: {}", exceptionEvent.toString());
        if (callback != null) {
            callback.onRegisterFailed("网络异常");
        }
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        // 事务终止
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        // 对话终止
    }

    // ========== Getter/Setter ==========

    public boolean isRegistered() {
        return registered.get();
    }

    public void setCallback(RegisterCallback callback) {
        this.callback = callback;
    }

    /**
     * ✅ 设置SipMessageManager引用，用于转发MESSAGE响应
     */
    public void setMessageManager(com.sip.client.message.SipMessageManager messageManager) {
        this.messageManager = messageManager;
        log.info("✅ 已设置MessageManager引用，将转发MESSAGE响应");
    }

    /**
     * 设置 CallManager 引用（用于转发 INVITE/BYE/ACK/CANCEL 请求）
     */
    public void setCallManager(com.sip.client.call.SipCallManager callManager) {
        this.callManager = callManager;
        log.info("✅ 已设置CallManager引用，将转发INVITE/BYE等请求");
    }

    // ========== 公共Getter方法 (供消息模块使用) ==========

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

    public String getLocalIp() {
        return localIp;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getSipPassword() {
        return password;
    }

    // ========== 回调接口 ==========

    public interface RegisterCallback {
        void onRegisterSuccess();
        void onRegisterFailed(String reason);
        void onUnregisterSuccess();

        /**
         * 收到SIP消息时回调
         * @param from 发送者用户名
         * @param messageBody 消息内容
         */
        default void onMessageReceived(String from, String messageBody) {
            // 默认空实现，子类可选择性实现
        }
    }
}
