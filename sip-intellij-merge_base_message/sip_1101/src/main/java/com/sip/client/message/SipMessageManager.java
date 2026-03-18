package com.sip.client.message;

import com.sip.client.core.SipManager;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * SIP 消息管理器 - 成员3核心类
 * 负责处理SIP MESSAGE方法的发送与接收
 *
 * 功能:
 * 1. 发送文本消息 (SIP MESSAGE)
 * 2. 接收文本消息
 * 3. 发送图片消息 (URL方式)
 * 4. 发送文件消息
 *
 * 设计: 基于core/SipManager的客户端业务逻辑
 *
 * @author 成员3 - SIP消息模块负责人
 * @version 1.0
 */
@Slf4j
public class SipMessageManager implements SipListener {

    // ========== 单例模式 ==========
    private static volatile SipMessageManager instance;

    public static SipMessageManager getInstance() {
        if (instance == null) {
            synchronized (SipMessageManager.class) {
                if (instance == null) {
                    instance = new SipMessageManager();
                }
            }
        }
        return instance;
    }

    // ========== 依赖core模块 ==========
    private Object sipManager;  // 可以是SipManager或SipManagerWrapper

    // 辅助方法：获取SipProvider
    private javax.sip.SipProvider getSipProvider() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getSipProvider();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getSipProvider();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private javax.sip.address.AddressFactory getAddressFactory() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getAddressFactory();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getAddressFactory();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private javax.sip.header.HeaderFactory getHeaderFactory() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getHeaderFactory();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getHeaderFactory();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private javax.sip.message.MessageFactory getMessageFactory() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getMessageFactory();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getMessageFactory();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private String getLocalIp() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getLocalIp();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getLocalIp();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private int getLocalPort() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getLocalPort();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getLocalPort();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    private String getTransport() {
        if (sipManager instanceof SipManager) {
            return ((SipManager) sipManager).getTransport();
        } else if (sipManager instanceof SipManagerWrapper) {
            return ((SipManagerWrapper) sipManager).getTransport();
        }
        throw new IllegalStateException("sipManager未正确初始化");
    }

    // ========== 用户信息 ==========
    private String localSipUri;      // 当前用户的SIP URI (例如: sip:alice@sip.example.com)
    private String username;         // 当前用户名
    private String sipPassword;      // SIP密码（用于认证）
    private String serverHost;       // Kamailio服务器地址
    private int serverPort;          // Kamailio服务器端口
    private boolean useP2P = false;  // 使用服务器模式（通过Kamailio路由）

    // ========== 认证相关 ==========
    private java.util.Map<String, Request> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private com.sip.client.register.SipAuthHandler authHandler;

    // ========== 消息回调 ==========
    private Consumer<IncomingMessage> messageCallback;  // 收到新消息时的回调

    // ========== 序列号管理 ==========
    private long cseqNumber = 1;

    private SipMessageManager() {
        // 私有构造函数
    }

    /**
     * 初始化消息管理器
     *
     * @param username 用户名
     * @param serverHost Kamailio服务器地址
     * @param serverPort Kamailio服务器端口
     */
    public void initialize(String username, String serverHost, int serverPort) throws Exception {
        log.info("========================================");
        log.info("初始化 SIP 消息管理器");
        log.info("用户名: {}", username);
        log.info("Kamailio: {}:{}", serverHost, serverPort);
        log.info("========================================");

        this.username = username;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localSipUri = "sip:" + username + "@" + serverHost;

        // 获取core模块实例
        this.sipManager = SipManager.getInstance();

        if (!((SipManager)sipManager).isInitialized()) {
            throw new IllegalStateException("SipManager 未初始化！请先调用 SipManager.getInstance().initialize()");
        }

        // 注册SIP监听器
        getSipProvider().addSipListener(this);

        log.info("SIP 消息管理器初始化完成");
        log.info("本地 SIP URI: {}", localSipUri);
    }

    /**
     * 初始化消息管理器 - 使用已存在的SipProvider
     * ✅ 更新：使用RegisterManager的公共getter方法，无需反射
     *
     * @param username 用户名
     * @param serverHost MSS服务器地址
     * @param serverPort MSS服务器端口
     * @param registerManager 注册管理器实例
     */
    public void initializeWithExistingProvider(String username, String serverHost, int serverPort,
                                              com.sip.client.register.SipRegisterManager registerManager) throws Exception {
        log.info("========================================");
        log.info("初始化 SIP 消息管理器 (复用已有SipProvider)");
        log.info("用户名: {}", username);
        log.info("MSS服务器: {}:{}", serverHost, serverPort);
        log.info("========================================");

        this.username = username;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localSipUri = "sip:" + username + "@" + serverHost;

        // ✅ 获取SIP密码（从RegisterManager）
        this.sipPassword = registerManager.getSipPassword();

        // ✅ 直接使用RegisterManager的公共getter方法(不再需要反射)
        javax.sip.SipProvider sipProvider = registerManager.getSipProvider();
        javax.sip.address.AddressFactory addressFactory = registerManager.getAddressFactory();
        javax.sip.header.HeaderFactory headerFactory = registerManager.getHeaderFactory();
        javax.sip.message.MessageFactory messageFactory = registerManager.getMessageFactory();

        // 创建SipManager包装器
        this.sipManager = new SipManagerWrapper(sipProvider, addressFactory,
                                                 headerFactory, messageFactory,
                                                 registerManager.getLocalIp(), registerManager.getLocalPort());

        // ✅ 初始化认证处理器
        this.authHandler = new com.sip.client.register.SipAuthHandler();

        // ❌ 不能注册监听器：SipProvider只允许一个监听器（RegisterManager已注册）
        // 策略：让RegisterManager转发MESSAGE相关的响应事件给我们
        // sipProvider.addSipListener(this);  // 会抛出TooManyListenersException

        log.info("SIP 消息管理器初始化完成（使用RegisterManager转发响应）");
        log.info("本地 SIP URI: {}", localSipUri);
        log.info("本地监听: {}:{}", registerManager.getLocalIp(), registerManager.getLocalPort());
    }

    /**
     * 内部包装类：包装RegisterManager的组件供MessageManager使用
     * 不继承SipManager，避免构造函数问题
     */
    private static class SipManagerWrapper {
        private final javax.sip.SipProvider sipProvider;
        private final javax.sip.address.AddressFactory addressFactory;
        private final javax.sip.header.HeaderFactory headerFactory;
        private final javax.sip.message.MessageFactory messageFactory;
        private final String localIp;
        private final int localPort;

        public SipManagerWrapper(javax.sip.SipProvider sipProvider,
                                javax.sip.address.AddressFactory addressFactory,
                                javax.sip.header.HeaderFactory headerFactory,
                                javax.sip.message.MessageFactory messageFactory,
                                String localIp,
                                int localPort) {
            this.sipProvider = sipProvider;
            this.addressFactory = addressFactory;
            this.headerFactory = headerFactory;
            this.messageFactory = messageFactory;
            this.localIp = localIp;
            this.localPort = localPort;
        }

        public javax.sip.SipProvider getSipProvider() {
            return sipProvider;
        }

        public javax.sip.address.AddressFactory getAddressFactory() {
            return addressFactory;
        }

        public javax.sip.header.HeaderFactory getHeaderFactory() {
            return headerFactory;
        }

        public javax.sip.message.MessageFactory getMessageFactory() {
            return messageFactory;
        }

        public String getLocalIp() {
            return localIp;
        }

        public int getLocalPort() {
            return localPort;
        }

        public String getTransport() {
            return sipProvider.getListeningPoints()[0].getTransport();
        }

        public boolean isInitialized() {
            return true;
        }
    }

    /**
     * 发送文本消息 - 核心功能
     * ✅ 2025-12-13 优化：增强错误日志，添加详细调试信息
     *
     * @param toUsername 接收者用户名
     * @param content 消息内容
     * @return 是否发送成功
     */
    public boolean sendTextMessage(String toUsername, String content) {
        try {
            log.info("====================================================");
            log.info("[发送消息]");
            log.info("发送者: {}", username);
            log.info("接收者: {}", toUsername);
            log.info("内容: {}", content);
            log.info("====================================================");

            // ✅ 验证SipProvider是否可用
            if (getSipProvider() == null) {
                log.error("❌ SipProvider为null，无法发送消息");
                return false;
            }

            // 1. 构造Request URI
            SipURI requestURI;
            if (useP2P) {
                // 点对点模式：直接发送到对方的监听端口
                int targetPort = getPortForUser(toUsername);
                log.info("[P2P模式] 直接发送到 {}:{}", serverHost, targetPort);
                requestURI = getAddressFactory()
                    .createSipURI(toUsername, serverHost);
                requestURI.setPort(targetPort);
            } else {
                // ✅ 服务器模式：Request-URI只包含用户名和域名，不包含端口号
                // MSS服务器会根据注册信息(Contact)查找用户的实际地址
                requestURI = getAddressFactory()
                    .createSipURI(toUsername, serverHost);
                // ❌ 不要设置端口号！requestURI.setPort(serverPort);
                log.info("[服务器模式] Request-URI: sip:{}@{}", toUsername, serverHost);
            }

            // 2. 构造From Header
            SipURI fromURI = getAddressFactory()
                .createSipURI(username, serverHost);
            Address fromAddress = getAddressFactory()
                .createAddress(localSipUri, fromURI);
            FromHeader fromHeader = getHeaderFactory()
                .createFromHeader(fromAddress, String.valueOf(System.currentTimeMillis()));

            // 3. 构造To Header
            String toSipUri = "sip:" + toUsername + "@" + serverHost;
            Address toAddress = getAddressFactory().createAddress(toSipUri);
            ToHeader toHeader = getHeaderFactory().createToHeader(toAddress, null);

            // 4. 构造Via Header
            String localIp = getLocalIp();
            int localPort = getLocalPort();
            ViaHeader viaHeader = getHeaderFactory()
                .createViaHeader(localIp, localPort, getTransport(), null);
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            viaHeaders.add(viaHeader);

            // 5. 构造CallId Header
            CallIdHeader callIdHeader = getSipProvider().getNewCallId();

            // 6. 构造CSeq Header
            CSeqHeader cSeqHeader = getHeaderFactory()
                .createCSeqHeader(cseqNumber++, Request.MESSAGE);

            // 7. 构造MaxForwards Header
            MaxForwardsHeader maxForwards = getHeaderFactory()
                .createMaxForwardsHeader(70);

            // 8. 创建MESSAGE请求
            Request request = getMessageFactory().createRequest(
                requestURI,
                Request.MESSAGE,
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwards
            );

            // 9. 设置Content-Type为text/plain
            ContentTypeHeader contentTypeHeader = getHeaderFactory()
                .createContentTypeHeader("text", "plain");
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            request.setContent(contentBytes, contentTypeHeader);

            // 10. 保存请求（用于401重发）
            CallIdHeader callIdHeader2 = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
            pendingRequests.put(callIdHeader2.getCallId(), request);

            // 11. 发送请求
            log.info("📤 正在创建ClientTransaction...");
            ClientTransaction transaction = getSipProvider()
                .getNewClientTransaction(request);

            log.info("📤 正在发送SIP MESSAGE请求...");
            transaction.sendRequest();

            log.info("✅ [SIP MESSAGE] 请求已发送");
            log.info("From: {}", localSipUri);
            log.info("To: {}", toSipUri);
            log.info("Call-ID: {}", callIdHeader.getCallId());
            log.info("CSeq: {}", cseqNumber - 1);
            log.info("Local: {}:{}", localIp, localPort);
            log.info("====================================================");

            return true;

        } catch (javax.sip.InvalidArgumentException e) {
            log.error("❌ [发送失败] 无效参数: {}", e.getMessage());
            log.error("详细堆栈:", e);
            return false;
        } catch (java.text.ParseException e) {
            log.error("❌ [发送失败] SIP URI解析错误: {}", e.getMessage());
            log.error("详细堆栈:", e);
            return false;
        } catch (javax.sip.SipException e) {
            log.error("❌ [发送失败] SIP协议异常: {}", e.getMessage());
            log.error("详细堆栈:", e);
            return false;
        } catch (Exception e) {
            log.error("❌ [发送失败] 未知异常: {}", e.getMessage());
            log.error("异常类型: {}", e.getClass().getName());
            log.error("详细堆栈:", e);
            return false;
        }
    }

    /**
     * 根据用户名获取对应的端口号（点对点模式使用）
     */
    private int getPortForUser(String username) {
        switch (username.toLowerCase()) {
            case "alice": return 5061;
            case "bob": return 5062;
            case "charlie": return 5063;
            default: return 5061 + Math.abs(username.hashCode() % 10);
        }
    }

    /**
     * 发送图片消息（URL方式）
     *
     * @param toUsername 接收者用户名
     * @param imageUrl 图片URL
     * @return 是否发送成功
     */
    public boolean sendImageMessage(String toUsername, String imageUrl) {
        try {
            log.info("[发送图片消息] 接收者: {}, URL: {}", toUsername, imageUrl);

            // 使用特殊标记发送图片消息
            String content = "[IMAGE]" + imageUrl;
            return sendTextMessage(toUsername, content);

        } catch (Exception e) {
            log.error("[发送图片失败]", e);
            return false;
        }
    }

    /**
     * 发送文件消息（URL方式）
     *
     * @param toUsername 接收者用户名
     * @param fileUrl 文件URL
     * @param fileName 文件名
     * @return 是否发送成功
     */
    public boolean sendFileMessage(String toUsername, String fileUrl, String fileName) {
        try {
            log.info("[发送文件消息] 接收者: {}, 文件: {}", toUsername, fileName);

            // 使用特殊标记发送文件消息
            String content = "[FILE]" + fileName + "|" + fileUrl;
            return sendTextMessage(toUsername, content);

        } catch (Exception e) {
            log.error("[发送文件失败]", e);
            return false;
        }
    }

    /**
     * 设置接收消息的回调函数
     *
     * @param callback 收到新消息时的回调
     */
    public void setMessageCallback(Consumer<IncomingMessage> callback) {
        this.messageCallback = callback;
    }

    // ========== 响应处理（由RegisterManager转发调用） ==========

    /**
     * ✅ 公共方法：处理MESSAGE响应（由RegisterManager转发调用）
     * 因为SipProvider只允许一个监听器，RegisterManager会将MESSAGE响应转发给这个方法
     */
    public void handleResponseEvent(ResponseEvent responseEvent) {
        processResponse(responseEvent);
    }

    // ========== SipListener 实现 ==========

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();

        if (Request.MESSAGE.equals(method)) {
            handleIncomingMessage(requestEvent);
        }
    }

    /**
     * 处理接收到的消息 - 核心功能
     */
    private void handleIncomingMessage(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();

            // 1. 解析消息头
            FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

            String fromUri = fromHeader.getAddress().getURI().toString();
            String toUri = toHeader.getAddress().getURI().toString();

            // 2. 解析消息内容
            byte[] rawContent = request.getRawContent();
            String content = new String(rawContent, StandardCharsets.UTF_8);

            log.info("====================================================");
            log.info("[收到消息]");
            log.info("From: {}", fromUri);
            log.info("To: {}", toUri);
            log.info("内容: {}", content);
            log.info("====================================================");

            // 3. 发送200 OK响应
            Response response = getMessageFactory()
                .createResponse(Response.OK, request);

            ServerTransaction serverTransaction = requestEvent.getServerTransaction();
            if (serverTransaction == null) {
                serverTransaction = getSipProvider()
                    .getNewServerTransaction(request);
            }
            serverTransaction.sendResponse(response);

            log.info("[200 OK] 响应已发送");

            // 4. 触发回调
            if (messageCallback != null) {
                IncomingMessage incomingMessage = new IncomingMessage(
                    fromUri,
                    toUri,
                    content,
                    System.currentTimeMillis()
                );
                messageCallback.accept(incomingMessage);
            }

        } catch (Exception e) {
            log.error("[处理接收消息失败]", e);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();

        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        String method = cseqHeader.getMethod();

        if (Request.MESSAGE.equals(method)) {
            if (statusCode == Response.OK) {
                log.info("✅ [消息送达确认] 200 OK");
                // 清除待重发请求
                CallIdHeader callId = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
                pendingRequests.remove(callId.getCallId());
            } else if (statusCode == Response.UNAUTHORIZED || statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {
                log.warn("⚠️  [收到认证质询] {} {}", statusCode, response.getReasonPhrase());
                // 处理401/407认证
                handleAuthenticationChallenge(responseEvent);
            } else {
                log.error("❌ [消息发送失败] {} {}", statusCode, response.getReasonPhrase());
            }
        }
    }

    /**
     * 处理401/407认证质询
     */
    private void handleAuthenticationChallenge(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            CallIdHeader callId = (CallIdHeader) response.getHeader(CallIdHeader.NAME);

            // 获取原始请求
            Request originalRequest = pendingRequests.get(callId.getCallId());
            if (originalRequest == null) {
                log.error("❌ 找不到原始MESSAGE请求，无法重发");
                return;
            }

            log.info("🔐 正在生成认证头...");

            // 获取 WWW-Authenticate 或 Proxy-Authenticate 头
            WWWAuthenticateHeader wwwAuthHeader = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
            ProxyAuthenticateHeader proxyAuthHeader = (ProxyAuthenticateHeader) response.getHeader(ProxyAuthenticateHeader.NAME);

            // 生成认证头
            AuthorizationHeader authHeader;
            if (wwwAuthHeader != null) {
                authHeader = authHandler.generateAuthHeader(
                        originalRequest, wwwAuthHeader, username, sipPassword, AuthorizationHeader.NAME);
            } else if (proxyAuthHeader != null) {
                authHeader = authHandler.generateAuthHeader(
                        originalRequest, proxyAuthHeader, username, sipPassword, ProxyAuthorizationHeader.NAME);
            } else {
                log.error("❌ 没有找到认证头");
                return;
            }

            // ✅ 创建新的请求（复制原始请求）
            Request newRequest = (Request) originalRequest.clone();

            // ✅ 移除旧的Via头，创建新的Via头（新的branch）
            newRequest.removeHeader(ViaHeader.NAME);
            ViaHeader newViaHeader = getHeaderFactory()
                .createViaHeader(getLocalIp(), getLocalPort(), getTransport(), null);
            newRequest.addHeader(newViaHeader);

            // 添加认证头
            newRequest.addHeader(authHeader);

            // 更新CSeq
            CSeqHeader cseq = (CSeqHeader) newRequest.getHeader(CSeqHeader.NAME);
            cseq.setSeqNumber(cseq.getSeqNumber() + 1);

            log.info("🔐 使用认证信息重发MESSAGE请求...");

            // 发送带认证的请求
            ClientTransaction newTransaction = getSipProvider().getNewClientTransaction(newRequest);
            newTransaction.sendRequest();

            log.info("✅ 已重发带认证的MESSAGE请求");

        } catch (Exception e) {
            log.error("❌ 处理认证失败", e);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("[请求超时]");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("[IO异常] Host: {}", exceptionEvent.getHost());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        log.debug("[事务终止]");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        log.debug("[对话终止]");
    }

    /**
     * 关闭消息管理器
     */
    public void shutdown() {
        log.info("关闭 SIP 消息管理器");
        // core模块会统一关闭，这里不需要额外操作
    }

    // ========== 内部类：接收到的消息 ==========

    /**
     * 接收到的消息对象
     */
    public static class IncomingMessage {
        private final String fromUri;
        private final String toUri;
        private final String content;
        private final long timestamp;

        public IncomingMessage(String fromUri, String toUri, String content, long timestamp) {
            this.fromUri = fromUri;
            this.toUri = toUri;
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getFromUri() {
            return fromUri;
        }

        public String getToUri() {
            return toUri;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /**
         * 从SIP URI中提取用户名
         * 例如: sip:alice@sip.example.com -> alice
         */
        public String getFromUsername() {
            return extractUsername(fromUri);
        }

        public String getToUsername() {
            return extractUsername(toUri);
        }

        private String extractUsername(String sipUri) {
            if (sipUri.startsWith("sip:")) {
                String userPart = sipUri.substring(4);  // 去掉 "sip:"
                int atIndex = userPart.indexOf('@');
                if (atIndex > 0) {
                    return userPart.substring(0, atIndex);
                }
            }
            return sipUri;
        }

        /**
         * 判断是否为图片消息
         */
        public boolean isImageMessage() {
            return content.startsWith("[IMAGE]");
        }

        /**
         * 判断是否为文件消息
         */
        public boolean isFileMessage() {
            return content.startsWith("[FILE]");
        }

        /**
         * 获取图片URL
         */
        public String getImageUrl() {
            if (isImageMessage()) {
                return content.substring(7);  // 去掉 "[IMAGE]"
            }
            return null;
        }

        /**
         * 获取文件信息
         */
        public String[] getFileInfo() {
            if (isFileMessage()) {
                String fileData = content.substring(6);  // 去掉 "[FILE]"
                return fileData.split("\\|");  // [0]=fileName, [1]=fileUrl
            }
            return null;
        }

        @Override
        public String toString() {
            return String.format("[消息] From: %s, To: %s, Content: %s",
                getFromUsername(), getToUsername(), content);
        }
    }
}
