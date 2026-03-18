package com.sip.client;

import com.sip.client.config.SipConfig;
import com.sip.common.dto.ParticipantDTO;
import com.sip.client.core.SipManager;
import com.sip.client.core.SipUserAgent;
import com.sip.client.media.MediaManager;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * SIP 会议管理器 (重构版 - 使用核心模块)
 *
 * 负责会议的 SIP 信令控制
 * 集成核心模块: SipManager (SIP信令) + MediaManager (音视频处理)
 *
 * @author SIP Team - Member 4
 * @version 2.0 - 集成核心模块
 */
@Slf4j
public class SipConferenceManager extends SipUserAgent {

    // ========== 会议状态 ==========
    private String conferenceUri;
    private Dialog dialog;
    private ConferenceEventListener eventListener;

    // ========== 媒体管理 ==========
    private MediaManager mediaManager;
    private boolean audioEnabled = true;
    private boolean videoEnabled = true;

    // ========== SIP 配置 - 从 application.yml 读取 ==========
    private String sipServerAddress = SipConfig.getSipServerHost();
    private int sipServerPort = SipConfig.getSipServerPort();

    /**
     * 会议事件监听器接口
     */
    public interface ConferenceEventListener {
        void onConnected();
        void onParticipantJoined(ParticipantDTO participant);
        void onParticipantLeft(ParticipantDTO participant);
        void onConferenceEnded();
        void onError(String errorMessage);
    }

    /**
     * 构造函数
     */
    public SipConferenceManager() {
        log.info("====================================================");
        log.info("创建 SIP 会议管理器 (使用核心模块)");
        log.info("====================================================");
    }

    /**
     * 初始化会议管理器（使用 SipRegisterManager 的连接）
     *
     * @param username 用户名
     * @param domain 域名
     */
    public void initWithRegisterManager(String username, String domain) throws Exception {
        log.info("使用 SipRegisterManager 初始化会议管理器: {}@{}", username, domain);

        // 1. 获取 SipRegisterManager 实例
        com.sip.client.register.SipRegisterManager registerManager =
            com.sip.client.register.SipRegisterManager.getInstance();

        if (registerManager == null) {
            throw new Exception("SipRegisterManager 未初始化，请先登录");
        }

        // 2. 从 SipRegisterManager 获取 SIP 组件
        this.sipProvider = registerManager.getSipProvider();
        this.addressFactory = registerManager.getAddressFactory();
        this.headerFactory = registerManager.getHeaderFactory();
        this.messageFactory = registerManager.getMessageFactory();

        if (this.sipProvider == null) {
            throw new Exception("SIP Provider 未就绪，请确保已登录");
        }

        // 3. 设置用户信息和本地地址信息
        this.localUsername = username;
        this.localDomain = domain;

        // 从 SipProvider 的监听点获取本地 IP 和端口
        try {
            ListeningPoint lp = this.sipProvider.getListeningPoint("udp");
            if (lp != null) {
                this.localIp = lp.getIPAddress();
                this.localPort = lp.getPort();
                log.info("获取本地地址: {}:{}", this.localIp, this.localPort);
            } else {
                // 如果没有 UDP 监听点，尝试获取其他类型
                ListeningPoint[] lps = this.sipProvider.getListeningPoints();
                if (lps != null && lps.length > 0) {
                    this.localIp = lps[0].getIPAddress();
                    this.localPort = lps[0].getPort();
                    log.info("获取本地地址（备用）: {}:{}", this.localIp, this.localPort);
                } else {
                    throw new Exception("无法获取本地地址信息");
                }
            }
        } catch (Exception e) {
            log.error("获取本地地址失败", e);
            throw new Exception("获取本地地址失败: " + e.getMessage());
        }

        // 4. 注册为 SipListener（如果还没注册）
        try {
            this.sipProvider.addSipListener(this);
        } catch (Exception e) {
            // 可能已经注册过了，忽略错误
            log.debug("addSipListener 失败（可能已注册）: {}", e.getMessage());
        }

        // 5. MediaManager 状态检查
        mediaManager = MediaManager.getInstance();
        log.info("MediaManager 状态: {}", mediaManager.isInitialized() ? "已初始化" : "未初始化");

        log.info("会议管理器初始化完成（使用 SipRegisterManager）!");
    }

    /**
     * 初始化会议管理器（重用已有 SipManager）
     *
     * @param username 用户名
     * @param domain 域名
     */
    public void initWithExistingSipManager(String username, String domain) throws Exception {
        log.info("使用现有 SipManager 初始化会议管理器: {}@{}", username, domain);

        // 1. 获取 SipManager 实例（必须已初始化）
        SipManager sipManager = SipManager.getInstance();

        if (!sipManager.isInitialized()) {
            throw new Exception("SipManager 未初始化，请先登录");
        }

        log.info("重用已有的 SipManager 实例（登录时已初始化）");

        // 2. 初始化 SipUserAgent 基类
        super.initialize(sipManager, username, domain);

        // 3. MediaManager 状态检查
        mediaManager = MediaManager.getInstance();
        log.info("MediaManager 状态: {}", mediaManager.isInitialized() ? "已初始化" : "未初始化");

        log.info("会议管理器初始化完成!");
    }

    /**
     * 初始化会议管理器
     *
     * @param localIp 本地IP地址
     * @param localPort 本地端口
     * @param username 用户名
     * @param domain 域名
     */
    public void init(String localIp, int localPort, String username, String domain) throws Exception {
        log.info("初始化会议管理器: {}@{} ({}:{})", username, domain, localIp, localPort);

        // 1. 获取 SipManager 实例（重用已有实例，不重新初始化）
        SipManager sipManager = SipManager.getInstance();

        // 如果 SipManager 未初始化，才进行初始化（使用会议专用端口）
        if (!sipManager.isInitialized()) {
            log.info("SipManager 未初始化，使用会议专用端口: {}", localPort);
            sipManager.initialize(localIp, localPort, "udp");
        } else {
            log.info("重用已有的 SipManager 实例（登录时已初始化）");
        }

        // 2. 初始化 SipUserAgent 基类
        super.initialize(sipManager, username, domain);

        // 3. MediaManager 初始化已在 ConferenceMediaHandler 中处理，这里跳过
        mediaManager = MediaManager.getInstance();
        log.info("MediaManager 状态: {}", mediaManager.isInitialized() ? "已初始化" : "未初始化");

        log.info("会议管理器初始化完成!");
    }

    /**
     * 子类初始化回调 (SipUserAgent 模板方法)
     */
    @Override
    protected void onInitialize() throws Exception {
        log.info("SipConferenceManager 初始化回调");
        // 可以在这里进行额外的初始化工作
    }

    /**
     * 加入会议
     *
     * @param conferenceUri 会议 URI
     * @param listener 事件监听器
     */
    public void joinConference(String conferenceUri, ConferenceEventListener listener) {
        this.conferenceUri = conferenceUri;
        this.eventListener = listener;

        try {
            log.info("加入会议: {}", conferenceUri);

            // 自动初始化（如果还未初始化）
            if (sipProvider == null) {
                log.info("SipConferenceManager 未初始化，进行自动初始化...");
                // 使用默认配置初始化
                init("0.0.0.0", 5080, "anonymous", sipServerAddress);
            }

            // 发送 INVITE 请求
            sendInvite(conferenceUri);
        } catch (Exception e) {
            log.error("加入会议失败", e);
            if (eventListener != null) {
                eventListener.onError("加入会议失败: " + e.getMessage());
            }
        }
    }

    /**
     * 发送 INVITE 请求
     */
    private void sendInvite(String targetUri) throws Exception {
        log.info("发送 INVITE: {}", targetUri);

        // 构建完整 URI
        String fullUri = targetUri.startsWith("sip:") ? targetUri : "sip:" + targetUri + "@" + sipServerAddress;

        // 创建 Request-URI
        SipURI requestUri = (SipURI) addressFactory.createURI(fullUri);

        // From header
        String fromUri = "sip:" + localUsername + "@" + localDomain;
        Address fromAddress = addressFactory.createAddress(fromUri);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "conference-tag");

        // To header
        Address toAddress = addressFactory.createAddress(fullUri);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // Via 头
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort, "udp", null);
        viaHeaders.add(viaHeader);

        // Call-ID
        CallIdHeader callIdHeader = sipProvider.getNewCallId();

        // CSeq
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

        // Max-Forwards
        MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

        // Contact 头
        SipURI contactUri = addressFactory.createSipURI(null, localIp);
        contactUri.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

        // 创建 INVITE 请求
        Request inviteRequest = messageFactory.createRequest(
            requestUri,
            Request.INVITE,
            callIdHeader,
            cSeqHeader,
            fromHeader,
            toHeader,
            viaHeaders,
            maxForwardsHeader
        );

        inviteRequest.addHeader(contactHeader);

        // Add Route header (通过 Kamailio 代理)
        SipURI routeUri = addressFactory.createSipURI(null, sipServerAddress);
        routeUri.setPort(sipServerPort);
        routeUri.setLrParam();  // loose routing
        Address routeAddress = addressFactory.createAddress(routeUri);
        RouteHeader routeHeader = headerFactory.createRouteHeader(routeAddress);
        inviteRequest.addHeader(routeHeader);

        // Add SDP
        String sdp = createSdpOffer();
        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
        inviteRequest.setContent(sdp, contentTypeHeader);

        // 发送请求
        ClientTransaction inviteTransaction = sipProvider.getNewClientTransaction(inviteRequest);
        inviteTransaction.sendRequest();

        log.info("INVITE 请求已发送");
    }

    /**
     * 创建 SDP Offer (使用 MediaManager 的端口)
     */
    private String createSdpOffer() {
        StringBuilder sdp = new StringBuilder();
        sdp.append("v=0\r\n");
        sdp.append("o=").append(localUsername).append(" 2890844526 2890844526 IN IP4 ").append(localIp).append("\r\n");
        sdp.append("s=Conference Call\r\n");
        sdp.append("c=IN IP4 ").append(localIp).append("\r\n");
        sdp.append("t=0 0\r\n");

        // 音频流 (使用 MediaManager 分配的端口)
        int audioPort = mediaManager.getCurrentAudioPort();
        sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 111\r\n");
        sdp.append("a=rtpmap:111 opus/48000/2\r\n");
        sdp.append("a=fmtp:111 minptime=10;useinbandfec=1\r\n");

        // 视频流 (使用 MediaManager 分配的端口)
        int videoPort = mediaManager.getCurrentVideoPort();
        sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
        sdp.append("a=rtpmap:96 H264/90000\r\n");
        sdp.append("a=fmtp:96 profile-level-id=42e01f;packetization-mode=1\r\n");

        return sdp.toString();
    }

    /**
     * 启动媒体会话 (在收到 200 OK 后调用)
     */
    private void startMedia(Response response) {
        try {
            log.info("启动媒体会话");

            // 解析 SDP Answer
            byte[] rawContent = response.getRawContent();
            String sdpAnswer = new String(rawContent);
            Map<String, Object> remoteMediaInfo = parseSdpAnswer(sdpAnswer);

            // 启动音频
            if (audioEnabled) {
                mediaManager.startAudioCall(remoteMediaInfo);
                log.info("音频会话已启动");
            }

            // 启动视频
            if (videoEnabled) {
                mediaManager.startVideoCall(remoteMediaInfo, null); // VideoRenderer 可以后续传入
                log.info("视频会话已启动");
            }

        } catch (Exception e) {
            log.error("启动媒体会话失败", e);
        }
    }

    /**
     * 解析 SDP Answer
     */
    private Map<String, Object> parseSdpAnswer(String sdp) {
        Map<String, Object> mediaInfo = new HashMap<>();

        // 简单解析 (实际项目中应该使用专门的 SDP 解析器)
        String[] lines = sdp.split("\r\n");
        String remoteIp = null;
        Integer audioPort = null;
        Integer videoPort = null;

        for (String line : lines) {
            if (line.startsWith("c=IN IP4 ")) {
                remoteIp = line.substring(9).trim();
            } else if (line.startsWith("m=audio ")) {
                String[] parts = line.split(" ");
                audioPort = Integer.parseInt(parts[1]);
            } else if (line.startsWith("m=video ")) {
                String[] parts = line.split(" ");
                videoPort = Integer.parseInt(parts[1]);
            }
        }

        mediaInfo.put("remoteIp", remoteIp);
        mediaInfo.put("audioPort", audioPort);
        mediaInfo.put("videoPort", videoPort);

        log.info("解析 SDP: remoteIp={}, audioPort={}, videoPort={}", remoteIp, audioPort, videoPort);
        return mediaInfo;
    }

    /**
     * 停止媒体会话
     */
    private void stopMedia() {
        if (mediaManager != null && mediaManager.isMediaActive()) {
            mediaManager.stopMedia();
            log.info("媒体会话已停止");
        }
    }

    /**
     * 离开会议
     */
    public void leaveConference() {
        try {
            // 1. 停止媒体
            stopMedia();

            // 2. 发送 BYE 请求
            if (dialog != null) {
                Request byeRequest = dialog.createRequest(Request.BYE);
                ClientTransaction byeTransaction = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(byeTransaction);
                log.info("BYE 请求已发送");
            }
        } catch (Exception e) {
            log.error("离开会议失败", e);
        }
    }

    /**
     * 结束会议 (主持人)
     */
    public void endConference() {
        log.info("结束会议");
        leaveConference();
    }

    /**
     * 更新媒体状态
     */
    public void updateMediaStatus(boolean audioEnabled, boolean videoEnabled) {
        this.audioEnabled = audioEnabled;
        this.videoEnabled = videoEnabled;
        log.info("更新媒体状态: audio={}, video={}", audioEnabled, videoEnabled);
        // TODO: 发送 re-INVITE 更新媒体
    }

    /**
     * 邀请参与者
     */
    public void inviteParticipant(Long userId) {
        log.info("邀请参与者: {}", userId);
        // TODO: 调用后端 API 邀请参与者
    }

    /**
     * 邀请参与者（通过用户名）
     */
    public void inviteParticipant(String username) {
        log.info("邀请参与者: {}", username);
        // TODO: 调用后端 API 邀请参与者（通过用户名）
        // 如果username是数字，尝试转换为Long调用上面的方法
        try {
            Long userId = Long.parseLong(username);
            inviteParticipant(userId);
        } catch (NumberFormatException e) {
            log.warn("无法将用户名转换为ID: {}", username);
            // TODO: 通过其他方式邀请（如通过SIP URI）
        }
    }

    /**
     * 移除参与者
     */
    public void removeParticipant(Long userId) {
        log.info("移除参与者: {}", userId);
        // TODO: 调用后端 API 移除参与者
    }

    // ========== SipUserAgent 抽象方法实现 ==========

    /**
     * 处理 SIP 请求 (模板方法)
     */
    @Override
    protected void handleRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();

        log.info("收到 SIP 请求: {}", method);

        try {
            if (Request.BYE.equals(method)) {
                // 处理 BYE 请求
                Response okResponse = messageFactory.createResponse(Response.OK, request);
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = sipProvider.getNewServerTransaction(request);
                }
                serverTransaction.sendResponse(okResponse);

                // 停止媒体
                stopMedia();

                log.info("会议已结束 (收到 BYE)");
                if (eventListener != null) {
                    eventListener.onConferenceEnded();
                }
            }
        } catch (Exception e) {
            log.error("处理请求失败", e);
        }
    }

    /**
     * 处理 SIP 响应 (模板方法)
     */
    @Override
    protected void handleResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();

        log.info("收到 SIP 响应: {}", statusCode);

        try {
            if (statusCode == Response.OK) {
                CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                String method = cSeqHeader.getMethod();

                if (Request.INVITE.equals(method)) {
                    // 收到 200 OK (INVITE)
                    dialog = responseEvent.getDialog();

                    // 发送 ACK
                    Request ackRequest = dialog.createAck(cSeqHeader.getSeqNumber());
                    dialog.sendAck(ackRequest);

                    // 启动媒体会话
                    startMedia(response);

                    log.info("会议连接已建立");
                    if (eventListener != null) {
                        eventListener.onConnected();
                    }
                }
            } else if (statusCode == Response.RINGING) {
                log.info("会议振铃中...");
            } else if (statusCode >= 400) {
                log.error("会议请求失败: {}", statusCode);
                if (eventListener != null) {
                    eventListener.onError("会议请求失败: " + statusCode);
                }
            }
        } catch (Exception e) {
            log.error("处理响应失败", e);
        }
    }

    /**
     * 处理超时 (SipUserAgent 已实现,可选覆盖)
     */
    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        super.processTimeout(timeoutEvent);
        log.warn("SIP 请求超时");
        if (eventListener != null) {
            eventListener.onError("请求超时");
        }
    }

    /**
     * 处理 IO 异常 (SipUserAgent 已实现,可选覆盖)
     */
    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        super.processIOException(exceptionEvent);
        log.error("SIP IO 异常: host={}, port={}", exceptionEvent.getHost(), exceptionEvent.getPort());
        if (eventListener != null) {
            eventListener.onError("网络异常");
        }
    }

    /**
     * 实现 SipUserAgent 的抽象方法
     * 处理超时事件
     */
    @Override
    protected void handleTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP 请求超时");
        if (eventListener != null) {
            eventListener.onError("请求超时");
        }
    }

    /**
     * 实现 SipUserAgent 的抽象方法
     * 当SipUserAgent关闭时调用
     */
    @Override
    protected void onShutdown() {
        log.info("SipConferenceManager onShutdown 被调用");
        shutdown();
    }

    /**
     * 关闭会议管理器
     */
    public void shutdown() {
        try {
            // 1. 停止媒体
            stopMedia();

            // 2. 移除监听器
            if (sipProvider != null) {
                sipProvider.removeSipListener(this);
            }

            log.info("SIP 会议管理器已关闭");
        } catch (Exception e) {
            log.error("关闭会议管理器失败", e);
        }
    }
}
