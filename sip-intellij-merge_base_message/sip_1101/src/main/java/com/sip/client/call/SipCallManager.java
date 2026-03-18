package com.sip.client.call;

import com.sip.client.core.SipManager;
import com.sip.client.register.SipAuthHandler;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP 呼叫管理器
 * 负责点对点音视频通话的 SIP 信令控制
 * 功能:
 * 1. 发起呼叫 (INVITE)
 * 2. 接听呼叫 (200 OK)
 * 3. 拒绝呼叫 (486 Busy Here / 603 Decline)
 * 4. 挂断通话 (BYE)
 * 5. 处理呼叫状态变化
 * 6. SDP 协商管理
 */
@Slf4j
public class SipCallManager implements SipListener {

    // ========== 单例模式 ==========
    private static volatile SipCallManager instance;

    public static SipCallManager getInstance() {
        if (instance == null) {
            synchronized (SipCallManager.class) {
                if (instance == null) {
                    instance = new SipCallManager();
                }
            }
        }
        return instance;
    }

    // ========== SIP 组件 ==========
    private SipStack sipStack;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;
    private ListeningPoint listeningPoint;

    // ========== 用户信息 ==========
    private String localUsername;
    private String localDomain;
    private String localIp;
    private int localPort;
    private String password;  // ✅ 添加密码字段用于认证

    // ========== 认证处理器 ==========
    private SipAuthHandler authHandler;

    // ========== 会话管理 ==========
    // CallId -> Dialog
    private Map<String, Dialog> activeDialogs = new ConcurrentHashMap<>();
    // CallId -> ServerTransaction (for incoming calls)
    private Map<String, ServerTransaction> pendingInvites = new ConcurrentHashMap<>();
    // CallId -> ClientTransaction (for outgoing calls)
    private Map<String, ClientTransaction> outgoingCalls = new ConcurrentHashMap<>();

    // ========== SDP 协商器 ==========
    private SdpNegotiator sdpNegotiator;

    // ========== 回调接口 ==========
    private CallEventListener callEventListener;

    // ========== 当前通话状态 ==========
    private CallState currentCallState = CallState.IDLE;
    private String currentCallId;

    // ========== 通话记录追踪 ==========
    private java.time.LocalDateTime callStartTime;      // 通话开始时间
    private String currentCallType;                      // 通话类型 (audio/video)
    private String currentTargetUsername;                // 通话对方用户名
    private boolean isIncomingCall;                      // 是否为来电

    private SipCallManager() {
        this.sdpNegotiator = new SdpNegotiator();
        this.authHandler = new SipAuthHandler();  // ✅ 初始化认证处理器
    }

    /**
     * 初始化 SIP 呼叫管理器（复用已有的 SipProvider）
     *
     * @param sipProvider 来自 RegisterManager 的 SipProvider（避免端口冲突）
     * @param addressFactory 地址工厂
     * @param headerFactory 头部工厂
     * @param messageFactory 消息工厂
     * @param localIp 本地IP
     * @param localPort 本地端口
     * @param username 用户名
     * @param domain 域名
     * @param password 密码（用于认证）
     */
    public void initialize(SipProvider sipProvider, AddressFactory addressFactory,
                         HeaderFactory headerFactory, MessageFactory messageFactory,
                         String localIp, int localPort, String username, String domain, String password) throws Exception {
        this.localIp = localIp;
        this.localPort = localPort;
        this.localUsername = username;
        this.localDomain = domain;
        this.password = password;  // ✅ 保存密码用于认证

        log.info("初始化 SipCallManager: {}@{} ({}:{})", username, domain, localIp, localPort);

        // ✅ 复用 RegisterManager 的 SipProvider，而不是创建新的
        this.sipProvider = sipProvider;
        this.addressFactory = addressFactory;
        this.headerFactory = headerFactory;
        this.messageFactory = messageFactory;

        // ❌ 不能调用 addSipListener - SipProvider 已经被 RegisterManager 使用
        // RegisterManager 会转发 INVITE/BYE 等请求到 SipCallManager
        // this.sipProvider.addSipListener(this);

        log.info("✅ SipCallManager 初始化成功（复用 SipProvider，通过 RegisterManager 转发请求）");
    }

    /**
     * 发起呼叫
     *
     * @param targetUsername 被叫用户名
     * @param callType 通话类型: audio / video
     * @param localAudioPort 本地音频 RTP 端口
     * @param localVideoPort 本地视频 RTP 端口 (如果是视频通话)
     */
    public void makeCall(String targetUsername, String callType, int localAudioPort, Integer localVideoPort) {
        try {
            // ✅ 添加初始化检查，防止 NullPointerException
            if (localUsername == null || localDomain == null || addressFactory == null) {
                String error = "SipCallManager 未正确初始化 (localUsername=" + localUsername +
                              ", localDomain=" + localDomain + ", addressFactory=" +
                              (addressFactory == null ? "null" : "ok") + ")";
                log.error(error);
                if (callEventListener != null) {
                    callEventListener.onCallFailed(error);
                }
                return;
            }

            if (currentCallState != CallState.IDLE) {
                log.warn("当前有通话进行中,无法发起新呼叫");
                if (callEventListener != null) {
                    callEventListener.onCallFailed("当前有通话进行中");
                }
                return;
            }

            log.info("发起呼叫: {} -> {}, 类型: {}", localUsername, targetUsername, callType);

            // 1. 创建请求 URI (被叫地址)
            SipURI requestURI = addressFactory.createSipURI(targetUsername, localDomain);

            // 2. 创建 From Header (主叫)
            SipURI fromURI = addressFactory.createSipURI(localUsername, localDomain);
            Address fromAddress = addressFactory.createAddress(fromURI);
            fromAddress.setDisplayName(localUsername);
            String fromTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, fromTag);

            // 3. 创建 To Header (被叫)
            SipURI toURI = addressFactory.createSipURI(targetUsername, localDomain);
            Address toAddress = addressFactory.createAddress(toURI);
            toAddress.setDisplayName(targetUsername);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null); // 没有 to-tag

            // 4. 创建 Via Header
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
            viaHeaders.add(viaHeader);

            // 5. 创建 Call-Id Header
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            this.currentCallId = callIdHeader.getCallId();

            // 6. 创建 CSeq Header
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

            // 7. 创建 Max-Forwards Header
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            // 8. 创建 Contact Header
            SipURI contactURI = addressFactory.createSipURI(localUsername, localIp);
            contactURI.setPort(localPort);
            Address contactAddress = addressFactory.createAddress(contactURI);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

            // 9. 创建 INVITE 请求
            Request inviteRequest = messageFactory.createRequest(
                requestURI,
                Request.INVITE,
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwardsHeader
            );
            inviteRequest.addHeader(contactHeader);

            // 10. 创建 SDP (通过 SdpNegotiator)
            String sdpOffer = sdpNegotiator.createOffer(
                localIp,
                callType,
                localAudioPort,
                localVideoPort
            );

            // 11. 设置 Content-Type 和 SDP Body
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(
                "application", "sdp"
            );
            inviteRequest.setContent(sdpOffer, contentTypeHeader);

            // 12. 创建客户端事务
            ClientTransaction inviteTransaction = sipProvider.getNewClientTransaction(inviteRequest);

            // 13. 保存到映射表
            outgoingCalls.put(currentCallId, inviteTransaction);

            // 14. 发送 INVITE
            inviteTransaction.sendRequest();

            // 15. 更新状态
            currentCallState = CallState.CALLING;

            // ✅ 记录通话信息
            this.callStartTime = java.time.LocalDateTime.now();
            this.currentCallType = callType;
            this.currentTargetUsername = targetUsername;
            this.isIncomingCall = false;

            log.info("INVITE 请求已发送: Call-Id = {}, 开始时间: {}", currentCallId, callStartTime);

            // 16. 通知 UI
            if (callEventListener != null) {
                callEventListener.onCalling(targetUsername);
            }

        } catch (Exception e) {
            log.error("发起呼叫失败", e);
            currentCallState = CallState.IDLE;
            if (callEventListener != null) {
                callEventListener.onCallFailed("发起呼叫失败: " + e.getMessage());
            }
        }
    }

    /**
     * 接听呼叫
     *
     * @param callId 呼叫 ID
     * @param localAudioPort 本地音频 RTP 端口
     * @param localVideoPort 本地视频 RTP 端口
     */
    public void answerCall(String callId, int localAudioPort, Integer localVideoPort) {
        try {
            log.info("接听呼叫: {}", callId);

            ServerTransaction serverTransaction = pendingInvites.get(callId);
            if (serverTransaction == null) {
                log.warn("未找到待接听的呼叫: {}", callId);
                return;
            }

            Request inviteRequest = serverTransaction.getRequest();

            // 1. 解析对方的 SDP Offer
            String remoteSdpOffer = new String(inviteRequest.getRawContent());
            String callType = sdpNegotiator.parseCallType(remoteSdpOffer);

            // 2. 创建 SDP Answer
            String sdpAnswer = sdpNegotiator.createAnswer(
                localIp,
                remoteSdpOffer,
                localAudioPort,
                localVideoPort
            );

            // 3. 创建 200 OK 响应
            Response okResponse = messageFactory.createResponse(Response.OK, inviteRequest);

            // 4. 添加 Contact Header
            SipURI contactURI = addressFactory.createSipURI(localUsername, localIp);
            contactURI.setPort(localPort);
            Address contactAddress = addressFactory.createAddress(contactURI);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
            okResponse.addHeader(contactHeader);

            // 5. 添加 To Tag (生成 Dialog)
            ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
            if (toHeader.getTag() == null) {
                String toTag = Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
                toHeader.setTag(toTag);
            }

            // 6. 设置 SDP Answer
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(
                "application", "sdp"
            );
            okResponse.setContent(sdpAnswer, contentTypeHeader);

            // 7. 发送 200 OK
            serverTransaction.sendResponse(okResponse);

            // 8. 获取 Dialog
            Dialog dialog = serverTransaction.getDialog();
            activeDialogs.put(callId, dialog);

            // 9. 更新状态
            currentCallState = CallState.IN_CALL;
            currentCallId = callId;

            log.info("✅ [DEBUG] answerCall完成: callId={}, callStartTime={}, currentTargetUsername={}, currentCallType={}, isIncomingCall={}",
                     callId, callStartTime, currentTargetUsername, currentCallType, isIncomingCall);

            log.info("200 OK 已发送: Call-Id = {}", callId);

            // 10. 通知 UI (开始 RTP 传输)
            if (callEventListener != null) {
                Map<String, Object> remoteMediaInfo = sdpNegotiator.parseRemoteSdp(remoteSdpOffer);
                callEventListener.onCallEstablished(remoteMediaInfo);
            }

        } catch (Exception e) {
            log.error("接听呼叫失败", e);
            if (callEventListener != null) {
                callEventListener.onCallFailed("接听失败: " + e.getMessage());
            }
        }
    }

    /**
     * 拒绝呼叫
     *
     * @param callId 呼叫 ID
     * @param reason 拒绝原因
     */
    public void rejectCall(String callId, String reason) {
        try {
            log.info("拒绝呼叫: {}, 原因: {}", callId, reason);

            ServerTransaction serverTransaction = pendingInvites.get(callId);
            if (serverTransaction == null) {
                log.warn("未找到待拒绝的呼叫: {}", callId);
                return;
            }

            Request inviteRequest = serverTransaction.getRequest();

            // 创建 486 Busy Here 或 603 Decline 响应
            int statusCode = "busy".equals(reason) ? Response.BUSY_HERE : Response.DECLINE;
            Response declineResponse = messageFactory.createResponse(statusCode, inviteRequest);

            // 发送响应
            serverTransaction.sendResponse(declineResponse);

            // 移除待处理呼叫
            pendingInvites.remove(callId);

            log.info("拒绝响应已发送: {}", statusCode);

        } catch (Exception e) {
            log.error("拒绝呼叫失败", e);
        }
    }

    /**
     * 挂断通话
     */
    public void hangupCall() {
        try {
            if (currentCallId == null) {
                log.warn("当前没有通话可挂断");
                return;
            }

            log.info("挂断通话: {}", currentCallId);

            Dialog dialog = activeDialogs.get(currentCallId);
            if (dialog == null) {
                log.warn("未找到活跃的 Dialog: {}", currentCallId);
                return;
            }

            // 1. 创建 BYE 请求
            Request byeRequest = dialog.createRequest(Request.BYE);

            // 2. 创建客户端事务
            ClientTransaction byeTransaction = sipProvider.getNewClientTransaction(byeRequest);

            // 3. 发送 BYE
            dialog.sendRequest(byeTransaction);

            log.info("BYE 请求已发送: Call-Id = {}", currentCallId);

            // 5. 清理资源
            cleanupCall(currentCallId);

        } catch (Exception e) {
            log.error("挂断通话失败", e);
        }
    }

    /**
     * 清理通话资源
     */
    private void cleanupCall(String callId) {
        log.info("🔍 [DEBUG] cleanupCall开始: callId={}, currentCallId={}, callStartTime={}, currentTargetUsername={}, currentCallType={}, isIncomingCall={}",
                 callId, currentCallId, callStartTime, currentTargetUsername, currentCallType, isIncomingCall);

        // ✅ 先复制需要保存的数据到局部变量（避免多线程竞态条件）
        final java.time.LocalDateTime savedCallStartTime = this.callStartTime;
        final String savedCallType = this.currentCallType;
        final String savedTargetUsername = this.currentTargetUsername;
        final boolean savedIsIncomingCall = this.isIncomingCall;
        final String savedLocalUsername = this.localUsername;

        // ✅ 立即清空字段，释放资源
        activeDialogs.remove(callId);
        pendingInvites.remove(callId);
        outgoingCalls.remove(callId);

        if (currentCallId != null && currentCallId.equals(callId)) {
            log.info("🔧 [DEBUG] 重置CallManager状态: IDLE");
            currentCallState = CallState.IDLE;
            currentCallId = null;

            // ✅ 清空通话记录追踪字段
            callStartTime = null;
            currentCallType = null;
            currentTargetUsername = null;
            isIncomingCall = false;

            // 通知 UI
            if (callEventListener != null) {
                callEventListener.onCallEnded();
            }

            log.info("✅ [DEBUG] CallManager状态已重置，可接受新的通话");
        }

        // ✅ 使用复制的数据异步保存通话记录
        // ⚡ 关键修复：只有主叫方保存通话记录，避免重复
        // 主叫方：isIncoming=false，被叫方：isIncoming=true
        if (savedCallStartTime != null && savedTargetUsername != null && !savedIsIncomingCall) {
            log.info("✅ [DEBUG] 准备调用saveCallRecord() - 使用已保存的数据（主叫方）");
            saveCallRecordAsync(savedCallStartTime, savedCallType, savedTargetUsername,
                               savedIsIncomingCall, savedLocalUsername);
        } else if (savedIsIncomingCall) {
            log.info("⏭️  [DEBUG] 跳过saveCallRecord: 被叫方不保存通话记录，避免重复");
        } else {
            log.warn("⚠️ [DEBUG] 跳过saveCallRecord: callStartTime={}, currentTargetUsername={}",
                     savedCallStartTime, savedTargetUsername);
        }
    }

    /**
     * 异步保存通话记录到服务器
     * 使用传入的参数而非实例字段，避免多线程竞态条件
     *
     * @param startTime 通话开始时间
     * @param callType 通话类型
     * @param targetUsername 对方用户名
     * @param isIncoming 是否为来电
     * @param localUser 本地用户名
     */
    private void saveCallRecordAsync(final java.time.LocalDateTime startTime,
                                     final String callType,
                                     final String targetUsername,
                                     final boolean isIncoming,
                                     final String localUser) {
        // 在新线程中执行，避免阻塞主线程
        new Thread(() -> {
            try {
                // 检查必要字段
                if (startTime == null) {
                    log.warn("⚠️  通话开始时间为空，无法保存记录");
                    return;
                }

                if (targetUsername == null) {
                    log.warn("⚠️  对方用户名为空，无法保存记录");
                    return;
                }

                // 计算通话时长（秒）
                java.time.LocalDateTime endTime = java.time.LocalDateTime.now();
                long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

                log.info("准备保存通话记录: 对方={}, 类型={}, 时长={}秒, 是否来电={}",
                        targetUsername, callType, durationSeconds, isIncoming);

                // ✅ 移除时长限制，即使0秒通话也保存（可能是取消、拒绝等情况）
                // 注意：如果需要区分状态，可以添加status字段

                // ✅ 转换SIP username到完整username (101 -> user101)
                String fullTargetUsername = targetUsername.startsWith("user") ?
                    targetUsername : "user" + targetUsername;
                String fullLocalUsername = localUser.startsWith("user") ?
                    localUser : "user" + localUser;

                // 构建请求URL
                String url = com.sip.client.config.SipConfig.getHttpServerUrl() + "/api/call/record";
                log.debug("保存通话记录URL: {}", url);

                // 构建JSON请求体
                String jsonBody = String.format(
                    "{\"callerUsername\":\"%s\",\"calleeUsername\":\"%s\",\"callType\":\"%s\",\"duration\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\"}",
                    isIncoming ? fullTargetUsername : fullLocalUsername,
                    isIncoming ? fullLocalUsername : fullTargetUsername,
                    callType,
                    durationSeconds,
                    startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    endTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );

                log.debug("请求体: {}", jsonBody);

                // 发送HTTP POST请求
                java.net.URL apiUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                // ✅ 添加认证Token
                String token = com.sip.client.util.HttpClientUtil.getAuthToken();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    log.debug("已添加Authorization header");
                } else {
                    log.warn("⚠️ Token为空，请求可能失败");
                }

                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                // 发送请求体
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // 检查响应
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // ✅ HTTP 200成功，但还需要检查响应body中的code字段
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        StringBuilder responseBody = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            responseBody.append(line.trim());
                        }

                        String response = responseBody.toString();
                        log.debug("服务器响应: {}", response);

                        // 简单检查：如果响应包含 "code":200，说明真正成功
                        if (response.contains("\"code\":200")) {
                            log.info("✅ 通话记录已保存到服务器");
                        } else {
                            log.warn("⚠️  保存通话记录失败（业务错误）: {}", response);
                        }
                    }
                } else {
                    log.warn("⚠️  保存通话记录失败: HTTP {}", responseCode);
                    // 读取错误响应
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                        StringBuilder responseBody = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            responseBody.append(line.trim());
                        }
                        log.warn("错误响应: {}", responseBody.toString());
                    }
                }

            } catch (Exception e) {
                log.error("❌ 保存通话记录失败", e);
            }
        }, "CallRecordSaver").start();
    }

    // ========== SipListener 实现 ==========

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            String method = request.getMethod();

            log.debug("收到 SIP 请求: {}", method);

            if (Request.INVITE.equals(method)) {
                handleIncomingInvite(requestEvent);
            } else if (Request.BYE.equals(method)) {
                handleIncomingBye(requestEvent);
            } else if (Request.ACK.equals(method)) {
                handleIncomingAck(requestEvent);
            } else if (Request.CANCEL.equals(method)) {
                handleIncomingCancel(requestEvent);
            }

        } catch (Exception e) {
            log.error("处理请求失败", e);
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            int statusCode = response.getStatusCode();
            CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            String method = cSeqHeader.getMethod();

            log.debug("收到 SIP 响应: {} {}", statusCode, method);

            if (Request.INVITE.equals(method)) {
                handleInviteResponse(responseEvent);
            } else if (Request.BYE.equals(method)) {
                handleByeResponse(responseEvent);
            }

        } catch (Exception e) {
            log.error("处理响应失败", e);
        }
    }

    /**
     * 处理接收到的 INVITE 请求 (来电)
     */
    private void handleIncomingInvite(RequestEvent requestEvent) {
        try {
            Request inviteRequest = requestEvent.getRequest();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();

            // 如果没有事务，创建一个
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(inviteRequest);
            }

            // 获取 Call-Id
            CallIdHeader callIdHeader = (CallIdHeader) inviteRequest.getHeader(CallIdHeader.NAME);
            String callId = callIdHeader.getCallId();

            // 获取主叫信息
            FromHeader fromHeader = (FromHeader) inviteRequest.getHeader(FromHeader.NAME);
            String callerUri = fromHeader.getAddress().toString();

            log.info("收到来电: {}, Call-Id: {}, 当前状态: {}", callerUri, callId, currentCallState);

            // ✅ 检查当前状态 - 如果不是IDLE，拒绝新的来电
            if (currentCallState != CallState.IDLE) {
                log.warn("⚠️  当前有通话进行中（状态: {}），拒绝新的来电", currentCallState);
                Response busyResponse = messageFactory.createResponse(Response.BUSY_HERE, inviteRequest);
                serverTransaction.sendResponse(busyResponse);
                return;
            }

            // 1. 发送 100 Trying
            Response tryingResponse = messageFactory.createResponse(Response.TRYING, inviteRequest);
            serverTransaction.sendResponse(tryingResponse);

            // 2. 解析 SDP
            String remoteSdpOffer = new String(inviteRequest.getRawContent());
            String callType = sdpNegotiator.parseCallType(remoteSdpOffer);

            // 3. 保存待处理呼叫
            pendingInvites.put(callId, serverTransaction);

            // ✅ 更新状态为RINGING（来电振铃中）
            currentCallState = CallState.RINGING;
            currentCallId = callId;

            // ✅ 记录来电信息（用于后续接听时保存）
            // 提取主叫用户名（从SIP URI中提取）
            String callerUsername = extractUsernameFromUri(callerUri);
            this.callStartTime = java.time.LocalDateTime.now();
            this.currentCallType = callType;
            this.currentTargetUsername = callerUsername;
            this.isIncomingCall = true;

            log.info("✅ [DEBUG] handleIncomingInvite记录来电信息: callStartTime={}, currentTargetUsername={}, currentCallType={}, isIncomingCall={}, 状态: RINGING",
                     callStartTime, currentTargetUsername, currentCallType, isIncomingCall);

            // 4. 发送 180 Ringing
            Response ringingResponse = messageFactory.createResponse(Response.RINGING, inviteRequest);
            serverTransaction.sendResponse(ringingResponse);

            log.info("已发送 180 Ringing");

            // 5. 通知 UI (振铃)
            if (callEventListener != null) {
                callEventListener.onIncomingCall(callId, callerUri, callType);
            }

        } catch (Exception e) {
            log.error("处理来电失败", e);
        }
    }

    /**
     * 处理 INVITE 响应 (呼叫结果)
     */
    private void handleInviteResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            int statusCode = response.getStatusCode();

            // 调试日志：打印statusCode和Response.UNAUTHORIZED的值
            log.info("DEBUG: statusCode={}, Response.UNAUTHORIZED={}, 是否相等={}",
                     statusCode, Response.UNAUTHORIZED, statusCode == Response.UNAUTHORIZED);

            if (statusCode == Response.TRYING || statusCode == Response.RINGING) {
                log.info("对方振铃中: {}", statusCode);
                if (callEventListener != null) {
                    callEventListener.onRinging();
                }

            } else if (statusCode == Response.OK) {
                log.info("对方接听了呼叫");

                // ✅ 重新设置通话开始时间为真正接通时间
                this.callStartTime = java.time.LocalDateTime.now();
                log.info("✅ [DEBUG] callStartTime已设置: {}, currentTargetUsername: {}, currentCallType: {}, isIncomingCall: {}",
                         callStartTime, currentTargetUsername, currentCallType, isIncomingCall);

                // 1. 发送 ACK
                ClientTransaction clientTransaction = responseEvent.getClientTransaction();
                Dialog dialog = clientTransaction.getDialog();
                Request ackRequest = dialog.createAck(1L);
                dialog.sendAck(ackRequest);

                log.info("ACK 已发送");

                // 2. 保存 Dialog
                activeDialogs.put(currentCallId, dialog);

                // 3. 解析 SDP Answer
                String remoteSdpAnswer = new String(response.getRawContent());
                Map<String, Object> remoteMediaInfo = sdpNegotiator.parseRemoteSdp(remoteSdpAnswer);

                // 4. 更新状态
                currentCallState = CallState.IN_CALL;

                // 5. 通知 UI (开始 RTP 传输)
                if (callEventListener != null) {
                    callEventListener.onCallEstablished(remoteMediaInfo);
                }

            } else if (statusCode == Response.UNAUTHORIZED) {
                // ✅ 处理 401 认证质询
                log.debug("收到 401 认证质询，准备重发 INVITE");
                handleInviteAuthChallenge(responseEvent);

            } else if (statusCode >= 400) {
                log.warn("呼叫失败: {}", statusCode);
                currentCallState = CallState.IDLE;

                if (callEventListener != null) {
                    String reason = getReasonPhrase(statusCode);
                    callEventListener.onCallFailed(reason);
                }
            }

        } catch (Exception e) {
            log.error("处理 INVITE 响应失败", e);
        }
    }

    /**
     * 处理 INVITE 401 认证质询
     */
    private void handleInviteAuthChallenge(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            ClientTransaction clientTransaction = responseEvent.getClientTransaction();
            Request originalRequest = clientTransaction.getRequest();

            log.debug("开始处理 INVITE 认证质询");

            // 1. 获取 WWW-Authenticate 头
            WWWAuthenticateHeader wwwAuthHeader = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
            if (wwwAuthHeader == null) {
                log.error("401 响应中未找到 WWW-Authenticate 头");
                currentCallState = CallState.IDLE;
                if (callEventListener != null) {
                    callEventListener.onCallFailed("认证失败：缺少认证信息");
                }
                return;
            }

            // 2. 生成 Authorization 头
            AuthorizationHeader authHeader = authHandler.generateAuthHeader(
                originalRequest, wwwAuthHeader, localUsername, password, AuthorizationHeader.NAME
            );

            // 3. 创建新的 INVITE 请求（带认证）
            // 获取原始请求的关键信息
            SipURI requestURI = (SipURI) originalRequest.getRequestURI();
            CallIdHeader callIdHeader = (CallIdHeader) originalRequest.getHeader(CallIdHeader.NAME);
            FromHeader fromHeader = (FromHeader) originalRequest.getHeader(FromHeader.NAME);
            ToHeader toHeader = (ToHeader) originalRequest.getHeader(ToHeader.NAME);
            ViaHeader viaHeader = (ViaHeader) originalRequest.getHeader(ViaHeader.NAME);

            // 创建新的 CSeq（序号加1）
            CSeqHeader oldCSeq = (CSeqHeader) originalRequest.getHeader(CSeqHeader.NAME);
            CSeqHeader newCSeq = headerFactory.createCSeqHeader(oldCSeq.getSeqNumber() + 1, Request.INVITE);

            // 创建 Max-Forwards Header
            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            // ✅ 创建新的 Via Header（让SIP栈自动生成新的branch参数，避免事务冲突）
            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader newViaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
            viaHeaders.add(newViaHeader);

            Request newInviteRequest = messageFactory.createRequest(
                requestURI,
                Request.INVITE,
                callIdHeader,
                newCSeq,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwardsHeader
            );

            // 4. 复制 Contact、Content-Type 和 SDP
            ContactHeader contactHeader = (ContactHeader) originalRequest.getHeader(ContactHeader.NAME);
            newInviteRequest.addHeader(contactHeader);

            ContentTypeHeader contentTypeHeader = (ContentTypeHeader) originalRequest.getHeader(ContentTypeHeader.NAME);
            if (contentTypeHeader != null && originalRequest.getRawContent() != null) {
                newInviteRequest.setContent(originalRequest.getRawContent(), contentTypeHeader);
            }

            // 5. 添加 Authorization 头
            newInviteRequest.addHeader(authHeader);

            // 6. 创建新的客户端事务并发送
            ClientTransaction newTransaction = sipProvider.getNewClientTransaction(newInviteRequest);

            // 更新映射表
            outgoingCalls.put(currentCallId, newTransaction);

            newTransaction.sendRequest();

            log.debug("✅ 已重新发送带认证的 INVITE 请求");

        } catch (Exception e) {
            log.error("处理 INVITE 认证质询失败", e);
            currentCallState = CallState.IDLE;
            if (callEventListener != null) {
                callEventListener.onCallFailed("认证失败: " + e.getMessage());
            }
        }
    }

    /**
     * 处理接收到的 BYE 请求 (对方挂断)
     */
    private void handleIncomingBye(RequestEvent requestEvent) {
        try {
            Request byeRequest = requestEvent.getRequest();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();

            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(byeRequest);
            }

            log.info("对方挂断了通话");

            // 1. 发送 200 OK
            Response okResponse = messageFactory.createResponse(Response.OK, byeRequest);
            serverTransaction.sendResponse(okResponse);

            // 2. 获取 Call-Id 并清理
            CallIdHeader callIdHeader = (CallIdHeader) byeRequest.getHeader(CallIdHeader.NAME);
            String callId = callIdHeader.getCallId();

            cleanupCall(callId);

        } catch (Exception e) {
            log.error("处理 BYE 请求失败", e);
        }
    }

    /**
     * 处理 BYE 响应 (挂断确认)
     */
    private void handleByeResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        log.info("收到 BYE 响应: {}", response.getStatusCode());
    }

    /**
     * 处理 ACK 请求
     */
    private void handleIncomingAck(RequestEvent requestEvent) {
        log.debug("收到 ACK 请求");
    }

    /**
     * 处理 CANCEL 请求 (取消呼叫)
     */
    private void handleIncomingCancel(RequestEvent requestEvent) {
        try {
            Request cancelRequest = requestEvent.getRequest();
            ServerTransaction serverTransaction = requestEvent.getServerTransaction();

            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(cancelRequest);
            }

            log.info("对方取消了呼叫");

            // 发送 200 OK
            Response okResponse = messageFactory.createResponse(Response.OK, cancelRequest);
            serverTransaction.sendResponse(okResponse);

            // 清理
            CallIdHeader callIdHeader = (CallIdHeader) cancelRequest.getHeader(CallIdHeader.NAME);
            String callId = callIdHeader.getCallId();
            cleanupCall(callId);

        } catch (Exception e) {
            log.error("处理 CANCEL 请求失败", e);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP 事务超时");
        if (callEventListener != null) {
            callEventListener.onCallFailed("呼叫超时");
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("SIP IO 异常", exceptionEvent);
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        log.debug("SIP 事务终止");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        log.debug("SIP Dialog 终止");
    }

    // ========== 工具方法 ==========

    /**
     * 从SIP URI中提取用户名
     * 例如: "sip:100@10.129.114.129" -> "100"
     * 或: "<sip:100@10.129.114.129>" -> "100"
     */
    private String extractUsernameFromUri(String sipUri) {
        if (sipUri == null) {
            return null;
        }

        // 去除尖括号
        String uri = sipUri.replaceAll("[<>]", "");

        // 查找 "sip:" 后面的部分
        int sipIndex = uri.indexOf("sip:");
        if (sipIndex == -1) {
            return uri; // 如果没有sip:前缀，直接返回
        }

        String afterSip = uri.substring(sipIndex + 4); // 跳过 "sip:"

        // 提取@之前的部分
        int atIndex = afterSip.indexOf('@');
        if (atIndex > 0) {
            return afterSip.substring(0, atIndex);
        }

        return afterSip;
    }

    private String getReasonPhrase(int statusCode) {
        switch (statusCode) {
            case Response.BUSY_HERE:
                return "对方忙";
            case Response.DECLINE:
                return "对方拒绝";
            case Response.NOT_FOUND:
                return "用户不存在";
            case Response.REQUEST_TIMEOUT:
                return "请求超时";
            default:
                return "呼叫失败 (状态码: " + statusCode + ")";
        }
    }

    // ========== Getter/Setter ==========

    public void setCallEventListener(CallEventListener listener) {
        this.callEventListener = listener;
    }

    /**
     * ✅ 获取当前的CallEventListener
     * 用于CallWindowController保存原始监听器
     */
    public CallEventListener getCurrentCallEventListener() {
        return this.callEventListener;
    }

    public CallState getCurrentCallState() {
        return currentCallState;
    }

    // ========== 回调接口 ==========

    /**
     * 通话事件监听器
     */
    public interface CallEventListener {
        /**
         * 呼叫中 (主叫方)
         */
        void onCalling(String targetUsername);

        /**
         * 来电 (被叫方)
         */
        void onIncomingCall(String callId, String callerUri, String callType);

        /**
         * 对方振铃
         */
        void onRinging();

        /**
         * 通话建立 (双方)
         * @param remoteMediaInfo 对方的媒体信息 (IP, 端口等)
         */
        void onCallEstablished(Map<String, Object> remoteMediaInfo);

        /**
         * 通话结束
         */
        void onCallEnded();

        /**
         * 通话失败
         */
        void onCallFailed(String reason);
    }

    /**
     * 通话状态枚举
     */
    public enum CallState {
        IDLE,       // 空闲
        CALLING,    // 呼叫中
        RINGING,    // 振铃中
        IN_CALL,    // 通话中
        ENDING      // 结束中
    }
}
