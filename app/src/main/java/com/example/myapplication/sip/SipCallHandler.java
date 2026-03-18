package com.example.myapplication.sip;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SIP 通话信令处理器
 *
 * 对应 PC 端 call/SipCallManager.java：
 * - INVITE 发送/接收（携带 SDP offer/answer）
 * - 100 Trying / 180 Ringing / 200 OK 响应处理
 * - ACK 发送（确认 200 OK）
 * - BYE 发送/接收（挂断）
 * - CANCEL 发送（取消未接通的呼叫）
 * - 401/407 Digest 认证重试
 * - 呼叫状态机管理
 */
public class SipCallHandler implements SipStack.SipPacketHandler {

    private static final String TAG = "SipCallHandler";

    // ===== 通话状态 =====
    public enum CallState {
        IDLE,       // 空闲
        CALLING,    // 正在呼出
        RINGING,    // 对方振铃 / 收到来电
        ACTIVE,     // 通话中
        ENDING      // 正在结束
    }

    // ===== 通话类型 =====
    public static final String CALL_TYPE_AUDIO = "audio";
    public static final String CALL_TYPE_VIDEO = "video";

    // ===== 回调接口（对应 PC 端 CallEventListener）=====
    public interface CallEventListener {
        void onCalling(String remoteUser, String callType);
        void onIncomingCall(String remoteUser, String callType, String callId);
        void onRinging();
        void onCallEstablished(SdpManager.MediaInfo remoteMedia);
        void onCallEnded(String reason);
        void onCallFailed(String reason);
    }

    private final SipStack stack;
    private final String sipServer;
    private final int sipServerPort;
    private String username;
    private String password;

    private final AtomicLong inviteCSeq = new AtomicLong(1);

    // ===== 当前通话状态 =====
    private volatile CallState state = CallState.IDLE;
    private volatile String currentCallId;
    private volatile String currentRemoteUser;
    private volatile String currentCallType;
    private volatile String remoteTag;
    private volatile String localTag;
    private volatile String inviteBranch;

    // 来电时保存 INVITE 原始信息（用于 200 OK 应答）
    private volatile String incomingInviteRaw;
    private volatile DatagramPacket incomingInvitePacket;

    private CallEventListener listener;

    // 本地音频 RTP 端口
    private int localAudioPort;
    private int localVideoPort;

    public SipCallHandler(SipStack stack, String sipServer, int sipServerPort) {
        this.stack = stack;
        this.sipServer = sipServer;
        this.sipServerPort = sipServerPort;
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void setCallEventListener(CallEventListener listener) {
        this.listener = listener;
    }

    public CallEventListener getCallEventListener() {
        return this.listener;
    }

    public CallState getState() { return state; }
    public String getCurrentCallId() { return currentCallId; }
    public String getCurrentRemoteUser() { return currentRemoteUser; }

    // ===== 发起呼叫（对应 PC 端 SipCallManager.makeCall）=====

    public void makeCall(String targetUsername, String callType, int audioPort, int videoPort) {
        if (state != CallState.IDLE) {
            Log.w(TAG, "当前已有通话，无法发起新呼叫");
            if (listener != null) listener.onCallFailed("已有通话进行中");
            return;
        }

        this.currentRemoteUser = targetUsername;
        this.currentCallType = callType;
        this.localAudioPort = audioPort;
        this.localVideoPort = videoPort;

        new Thread(() -> {
            try {
                currentCallId = SipStack.generateCallId();
                localTag = SipStack.generateTag();
                inviteBranch = SipStack.generateBranch();
                long seq = inviteCSeq.getAndIncrement();

                String localIp = stack.getLocalIp();
                int localPort = stack.getLocalPort();

                // 构造 SDP Offer
                String sdpBody = SdpManager.createOffer(localIp, callType, audioPort, videoPort);
                Log.i(TAG, "发送 SDP Offer (localIp=" + localIp
                        + ", audioPort=" + audioPort + ", videoPort=" + videoPort + "):\n" + sdpBody);
                byte[] sdpBytes = sdpBody.getBytes(StandardCharsets.UTF_8);

                StringBuilder sb = new StringBuilder();
                sb.append("INVITE sip:").append(targetUsername).append("@").append(sipServer)
                        .append(" SIP/2.0\r\n");
                sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                        .append(";branch=").append(inviteBranch).append("\r\n");
                sb.append("From: <sip:").append(username).append("@").append(sipServer)
                        .append(">;tag=").append(localTag).append("\r\n");
                sb.append("To: <sip:").append(targetUsername).append("@").append(sipServer)
                        .append(">\r\n");
                sb.append("Call-ID: ").append(currentCallId).append("\r\n");
                sb.append("CSeq: ").append(seq).append(" INVITE\r\n");
                sb.append("Max-Forwards: 70\r\n");
                sb.append("Contact: <sip:").append(username).append("@").append(localIp)
                        .append(":").append(localPort).append(">\r\n");
                sb.append("Content-Type: application/sdp\r\n");
                sb.append("Content-Length: ").append(sdpBytes.length).append("\r\n");
                sb.append("\r\n");
                sb.append(sdpBody);

                byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
                stack.send(data, InetAddress.getByName(sipServer), sipServerPort);

                state = CallState.CALLING;
                Log.i(TAG, "INVITE 已发送 -> " + targetUsername + " (type=" + callType + ")");
                if (listener != null) listener.onCalling(targetUsername, callType);

            } catch (Exception e) {
                Log.e(TAG, "发送 INVITE 失败", e);
                state = CallState.IDLE;
                if (listener != null) listener.onCallFailed("呼叫失败: " + e.getMessage());
            }
        }, "SIP-INVITE").start();
    }

    // ===== 接听来电（对应 PC 端 SipCallManager.answerCall）=====

    public void answerCall(int audioPort, int videoPort) {
        if (state != CallState.RINGING || incomingInviteRaw == null) {
            Log.w(TAG, "无来电可接听");
            return;
        }

        this.localAudioPort = audioPort;
        this.localVideoPort = videoPort;

        new Thread(() -> {
            try {
                // 解析来电 INVITE 中的 SDP
                String remoteSdp = extractSdpBody(incomingInviteRaw);
                String localIp = stack.getLocalIp();
                int localPort = stack.getLocalPort();

                // 构造 SDP Answer
                String sdpAnswer = SdpManager.createAnswer(localIp, remoteSdp, audioPort, videoPort);
                byte[] sdpBytes = sdpAnswer.getBytes(StandardCharsets.UTF_8);

                // 提取原始 INVITE 的头信息
                String via = SipStack.extractHeader(incomingInviteRaw, "Via");
                String from = SipStack.extractHeader(incomingInviteRaw, "From");
                String to = SipStack.extractHeader(incomingInviteRaw, "To");
                String callIdVal = SipStack.extractHeader(incomingInviteRaw, "Call-ID");
                String cseqVal = SipStack.extractHeader(incomingInviteRaw, "CSeq");

                // To 头需要加 tag
                if (localTag == null) localTag = SipStack.generateTag();
                String toWithTag = to;
                if (to != null && !to.contains("tag=")) {
                    toWithTag = to + ";tag=" + localTag;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("SIP/2.0 200 OK\r\n");
                if (via != null) sb.append("Via: ").append(via).append("\r\n");
                if (from != null) sb.append("From: ").append(from).append("\r\n");
                sb.append("To: ").append(toWithTag).append("\r\n");
                if (callIdVal != null) sb.append("Call-ID: ").append(callIdVal).append("\r\n");
                if (cseqVal != null) sb.append("CSeq: ").append(cseqVal).append("\r\n");
                sb.append("Contact: <sip:").append(username).append("@").append(localIp)
                        .append(":").append(localPort).append(">\r\n");
                sb.append("Content-Type: application/sdp\r\n");
                sb.append("Content-Length: ").append(sdpBytes.length).append("\r\n");
                sb.append("\r\n");
                sb.append(sdpAnswer);

                byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
                stack.send(data, InetAddress.getByName(sipServer), sipServerPort);

                state = CallState.ACTIVE;
                SdpManager.MediaInfo remoteMedia = SdpManager.parseRemoteSdp(remoteSdp);
                Log.i(TAG, "200 OK(INVITE) 已发送, 通话建立");
                Log.i(TAG, "远端SDP(来电INVITE中的):\n" + remoteSdp);
                Log.i(TAG, "解析远端媒体信息: " + remoteMedia);
                Log.i(TAG, "本地SDP Answer:\n" + sdpAnswer);
                if (listener != null) listener.onCallEstablished(remoteMedia);

            } catch (Exception e) {
                Log.e(TAG, "接听来电失败", e);
                if (listener != null) listener.onCallFailed("接听失败: " + e.getMessage());
            }
        }, "SIP-Answer").start();
    }

    // ===== 拒接来电 =====

    public void rejectCall() {
        if (state != CallState.RINGING || incomingInviteRaw == null) return;

        new Thread(() -> {
            try {
                sendSimpleResponse(incomingInviteRaw, 486, "Busy Here");
                state = CallState.IDLE;
                clearCallState();
                Log.i(TAG, "已拒接来电");
                if (listener != null) listener.onCallEnded("已拒接");
            } catch (Exception e) {
                Log.e(TAG, "拒接失败", e);
            }
        }, "SIP-Reject").start();
    }

    // ===== 挂断通话（对应 PC 端 SipCallManager.hangupCall）=====

    public void hangup() {
        if (state == CallState.IDLE) return;

        CallState prevState = state;
        state = CallState.ENDING;

        new Thread(() -> {
            try {
                if (prevState == CallState.CALLING) {
                    // 呼出中取消：发 CANCEL
                    sendCancel();
                } else {
                    // 通话中 / 振铃中：发 BYE
                    sendBye();
                }
                state = CallState.IDLE;
                clearCallState();
                Log.i(TAG, "通话已挂断");
                if (listener != null) listener.onCallEnded("通话结束");
            } catch (Exception e) {
                Log.e(TAG, "挂断失败", e);
                state = CallState.IDLE;
                clearCallState();
            }
        }, "SIP-Hangup").start();
    }

    // ===== SipPacketHandler 实现 =====

    @Override
    public void handleResponse(int statusCode, String method, String rawMessage, DatagramPacket packet) {
        String callId = SipStack.extractHeader(rawMessage, "Call-ID");
        String cseqMethod = SipStack.extractCSeqMethod(rawMessage);

        if ("INVITE".equals(cseqMethod)) {
            handleInviteResponse(statusCode, rawMessage);
        } else if ("BYE".equals(cseqMethod)) {
            if (statusCode == 200) {
                Log.d(TAG, "BYE 确认 200 OK");
            }
        } else if ("CANCEL".equals(cseqMethod)) {
            if (statusCode == 200) {
                Log.d(TAG, "CANCEL 确认 200 OK");
            }
        }
    }

    @Override
    public void handleRequest(String method, String rawMessage, DatagramPacket packet) {
        switch (method) {
            case "INVITE":
                handleIncomingInvite(rawMessage, packet);
                break;
            case "ACK":
                handleAck(rawMessage);
                break;
            case "BYE":
                handleIncomingBye(rawMessage, packet);
                break;
            case "CANCEL":
                handleIncomingCancel(rawMessage, packet);
                break;
        }
    }

    // ===== INVITE 响应处理 =====

    private void handleInviteResponse(int statusCode, String rawMessage) {
        if (statusCode == 100) {
            Log.d(TAG, "收到 100 Trying");
        } else if (statusCode == 180 || statusCode == 183) {
            Log.d(TAG, "收到 " + statusCode + " Ringing");
            // 提取远端 tag
            String toHeader = SipStack.extractHeader(rawMessage, "To");
            if (toHeader != null && toHeader.contains("tag=")) {
                remoteTag = extractTagFromHeader(toHeader);
            }
            if (state == CallState.CALLING) {
                state = CallState.RINGING;
                if (listener != null) listener.onRinging();
            }
        } else if (statusCode == 200) {
            Log.i(TAG, "收到 INVITE 200 OK，通话建立");
            // 提取远端 tag
            String toHeader = SipStack.extractHeader(rawMessage, "To");
            if (toHeader != null && toHeader.contains("tag=")) {
                remoteTag = extractTagFromHeader(toHeader);
            }
            // 发送 ACK
            sendAck(rawMessage);
            // 解析远端 SDP
            String remoteSdp = extractSdpBody(rawMessage);
            Log.i(TAG, "远端SDP内容:\n" + remoteSdp);
            SdpManager.MediaInfo remoteMedia = SdpManager.parseRemoteSdp(remoteSdp);
            Log.i(TAG, "解析远端媒体信息: " + remoteMedia);
            state = CallState.ACTIVE;
            if (listener != null) listener.onCallEstablished(remoteMedia);

        } else if (statusCode == 401 || statusCode == 407) {
            handleInviteAuthChallenge(statusCode, rawMessage);

        } else if (statusCode == 486 || statusCode == 603) {
            Log.i(TAG, "对方忙/拒绝: " + statusCode);
            // 发 ACK 确认非 2xx 响应
            sendAck(rawMessage);
            state = CallState.IDLE;
            clearCallState();
            if (listener != null) listener.onCallEnded(statusCode == 486 ? "对方忙" : "对方拒接");

        } else if (statusCode == 487) {
            // CANCEL 后收到 487 Request Terminated
            Log.d(TAG, "收到 487 Request Terminated");
            sendAck(rawMessage);
            state = CallState.IDLE;
            clearCallState();

        } else if (statusCode >= 400) {
            Log.w(TAG, "INVITE 失败: " + statusCode);
            sendAck(rawMessage);
            state = CallState.IDLE;
            clearCallState();
            if (listener != null) listener.onCallFailed("呼叫失败: " + statusCode);
        }
    }

    // ===== INVITE 401/407 认证重试 =====

    private void handleInviteAuthChallenge(int statusCode, String rawMessage) {
        try {
            String authHeaderName = (statusCode == 401) ? "WWW-Authenticate" : "Proxy-Authenticate";
            String authType = (statusCode == 401) ? "Authorization" : "Proxy-Authorization";

            String authLine = SipStack.extractHeader(rawMessage, authHeaderName);
            if (authLine == null) {
                if (listener != null) listener.onCallFailed("认证头缺失");
                return;
            }

            String realm = SipStack.extractDigestParam(authLine, "realm");
            String nonce = SipStack.extractDigestParam(authLine, "nonce");

            // 重新发送带认证的 INVITE
            String newCallId = SipStack.generateCallId();
            currentCallId = newCallId;
            long seq = inviteCSeq.getAndIncrement();
            String branch = SipStack.generateBranch();
            inviteBranch = branch;
            String localIp = stack.getLocalIp();
            int localPort = stack.getLocalPort();

            String requestUri = "sip:" + currentRemoteUser + "@" + sipServer;
            String sdpBody = SdpManager.createOffer(localIp, currentCallType, localAudioPort, localVideoPort);
            byte[] sdpBytes = sdpBody.getBytes(StandardCharsets.UTF_8);

            String ha1 = SipStack.md5(username + ":" + realm + ":" + password);
            String ha2 = SipStack.md5("INVITE:" + requestUri);
            String response = SipStack.md5(ha1 + ":" + nonce + ":" + ha2);

            StringBuilder sb = new StringBuilder();
            sb.append("INVITE ").append(requestUri).append(" SIP/2.0\r\n");
            sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                    .append(";branch=").append(branch).append("\r\n");
            sb.append("From: <sip:").append(username).append("@").append(sipServer)
                    .append(">;tag=").append(localTag).append("\r\n");
            sb.append("To: <sip:").append(currentRemoteUser).append("@").append(sipServer)
                    .append(">\r\n");
            sb.append("Call-ID: ").append(newCallId).append("\r\n");
            sb.append("CSeq: ").append(seq).append(" INVITE\r\n");
            sb.append("Max-Forwards: 70\r\n");
            sb.append("Contact: <sip:").append(username).append("@").append(localIp)
                    .append(":").append(localPort).append(">\r\n");
            sb.append(authType).append(": Digest username=\"").append(username)
                    .append("\",realm=\"").append(realm)
                    .append("\",nonce=\"").append(nonce)
                    .append("\",uri=\"").append(requestUri)
                    .append("\",response=\"").append(response)
                    .append("\",algorithm=MD5\r\n");
            sb.append("Content-Type: application/sdp\r\n");
            sb.append("Content-Length: ").append(sdpBytes.length).append("\r\n");
            sb.append("\r\n");
            sb.append(sdpBody);

            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
            Log.i(TAG, "INVITE 认证重发 -> " + currentRemoteUser);

        } catch (Exception e) {
            Log.e(TAG, "INVITE 认证重试失败", e);
            state = CallState.IDLE;
            clearCallState();
            if (listener != null) listener.onCallFailed("认证失败");
        }
    }

    // ===== 收到来电 INVITE =====

    private void handleIncomingInvite(String rawMessage, DatagramPacket packet) {
        if (state != CallState.IDLE) {
            // 已有通话，回复 486 Busy
            try {
                sendSimpleResponse(rawMessage, 486, "Busy Here");
            } catch (Exception e) {
                Log.e(TAG, "回复 486 失败", e);
            }
            return;
        }

        // 保存来电信息
        incomingInviteRaw = rawMessage;
        incomingInvitePacket = packet;

        // 提取呼叫者
        String fromHeader = SipStack.extractHeader(rawMessage, "From");
        String fromUser = extractUsernameFromHeader(fromHeader);
        currentCallId = SipStack.extractHeader(rawMessage, "Call-ID");

        // 提取远端 tag
        if (fromHeader != null && fromHeader.contains("tag=")) {
            remoteTag = extractTagFromHeader(fromHeader);
        }
        localTag = SipStack.generateTag();

        // 判断通话类型
        String sdpBody = extractSdpBody(rawMessage);
        currentCallType = SdpManager.parseCallType(sdpBody);
        currentRemoteUser = fromUser;

        Log.i(TAG, "收到来电 INVITE, SDP内容:\n" + sdpBody);
        Log.i(TAG, "解析通话类型: " + currentCallType);

        state = CallState.RINGING;

        // 发送 180 Ringing
        try {
            send180Ringing(rawMessage);
        } catch (Exception e) {
            Log.e(TAG, "发送 180 Ringing 失败", e);
        }

        Log.i(TAG, "收到来电 from: " + fromUser + " (type=" + currentCallType + ")");
        if (listener != null) listener.onIncomingCall(fromUser, currentCallType, currentCallId);
    }

    // ===== 收到 ACK =====

    private void handleAck(String rawMessage) {
        Log.d(TAG, "收到 ACK，通话已确认");
        // ACK 确认 200 OK，被叫方收到此 ACK 表示通话正式建立
    }

    // ===== 收到 BYE =====

    private void handleIncomingBye(String rawMessage, DatagramPacket packet) {
        try {
            sendSimpleResponse(rawMessage, 200, "OK");
        } catch (Exception e) {
            Log.e(TAG, "回复 BYE 200 OK 失败", e);
        }

        Log.i(TAG, "收到 BYE，通话结束");
        state = CallState.IDLE;
        clearCallState();
        if (listener != null) listener.onCallEnded("对方挂断");
    }

    // ===== 收到 CANCEL =====

    private void handleIncomingCancel(String rawMessage, DatagramPacket packet) {
        try {
            // 回复 CANCEL 200 OK
            sendSimpleResponse(rawMessage, 200, "OK");
            // 回复原始 INVITE 487 Request Terminated
            if (incomingInviteRaw != null) {
                sendSimpleResponse(incomingInviteRaw, 487, "Request Terminated");
            }
        } catch (Exception e) {
            Log.e(TAG, "处理 CANCEL 失败", e);
        }

        Log.i(TAG, "收到 CANCEL，呼叫已取消");
        state = CallState.IDLE;
        clearCallState();
        if (listener != null) listener.onCallEnded("对方取消");
    }

    // ===== 辅助方法：发送 SIP 请求/响应 =====

    private void sendAck(String inviteResponse) {
        try {
            String toHeader = SipStack.extractHeader(inviteResponse, "To");
            String fromHeader = SipStack.extractHeader(inviteResponse, "From");
            String callIdVal = SipStack.extractHeader(inviteResponse, "Call-ID");
            String cseqVal = SipStack.extractHeader(inviteResponse, "CSeq");

            String localIp = stack.getLocalIp();
            int localPort = stack.getLocalPort();

            // 提取 CSeq 序号
            String cseqNum = "1";
            if (cseqVal != null) {
                String[] parts = cseqVal.trim().split("\\s+");
                if (parts.length > 0) cseqNum = parts[0];
            }

            String targetUri = "sip:" + (currentRemoteUser != null ? currentRemoteUser : "") + "@" + sipServer;

            StringBuilder sb = new StringBuilder();
            sb.append("ACK ").append(targetUri).append(" SIP/2.0\r\n");
            sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                    .append(";branch=").append(SipStack.generateBranch()).append("\r\n");
            if (fromHeader != null) sb.append("From: ").append(fromHeader).append("\r\n");
            if (toHeader != null) sb.append("To: ").append(toHeader).append("\r\n");
            if (callIdVal != null) sb.append("Call-ID: ").append(callIdVal).append("\r\n");
            sb.append("CSeq: ").append(cseqNum).append(" ACK\r\n");
            sb.append("Max-Forwards: 70\r\n");
            sb.append("Content-Length: 0\r\n\r\n");

            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
            Log.d(TAG, "ACK 已发送");
        } catch (Exception e) {
            Log.e(TAG, "发送 ACK 失败", e);
        }
    }

    private void sendBye() throws Exception {
        String localIp = stack.getLocalIp();
        int localPort = stack.getLocalPort();
        long seq = inviteCSeq.getAndIncrement();
        String targetUri = "sip:" + currentRemoteUser + "@" + sipServer;

        StringBuilder sb = new StringBuilder();
        sb.append("BYE ").append(targetUri).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                .append(";branch=").append(SipStack.generateBranch()).append("\r\n");
        sb.append("From: <sip:").append(username).append("@").append(sipServer)
                .append(">;tag=").append(localTag != null ? localTag : SipStack.generateTag()).append("\r\n");
        sb.append("To: <sip:").append(currentRemoteUser).append("@").append(sipServer)
                .append(">");
        if (remoteTag != null) sb.append(";tag=").append(remoteTag);
        sb.append("\r\n");
        sb.append("Call-ID: ").append(currentCallId).append("\r\n");
        sb.append("CSeq: ").append(seq).append(" BYE\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Content-Length: 0\r\n\r\n");

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
        Log.i(TAG, "BYE 已发送");
    }

    private void sendCancel() throws Exception {
        String localIp = stack.getLocalIp();
        int localPort = stack.getLocalPort();
        String targetUri = "sip:" + currentRemoteUser + "@" + sipServer;

        StringBuilder sb = new StringBuilder();
        sb.append("CANCEL ").append(targetUri).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                .append(";branch=").append(inviteBranch).append("\r\n");
        sb.append("From: <sip:").append(username).append("@").append(sipServer)
                .append(">;tag=").append(localTag).append("\r\n");
        sb.append("To: <sip:").append(currentRemoteUser).append("@").append(sipServer)
                .append(">\r\n");
        sb.append("Call-ID: ").append(currentCallId).append("\r\n");
        sb.append("CSeq: 1 CANCEL\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Content-Length: 0\r\n\r\n");

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
        Log.i(TAG, "CANCEL 已发送");
    }

    private void send180Ringing(String inviteRequest) throws Exception {
        String via = SipStack.extractHeader(inviteRequest, "Via");
        String from = SipStack.extractHeader(inviteRequest, "From");
        String to = SipStack.extractHeader(inviteRequest, "To");
        String callIdVal = SipStack.extractHeader(inviteRequest, "Call-ID");
        String cseqVal = SipStack.extractHeader(inviteRequest, "CSeq");

        String toWithTag = to;
        if (to != null && !to.contains("tag=")) {
            toWithTag = to + ";tag=" + localTag;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SIP/2.0 180 Ringing\r\n");
        if (via != null) sb.append("Via: ").append(via).append("\r\n");
        if (from != null) sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(toWithTag).append("\r\n");
        if (callIdVal != null) sb.append("Call-ID: ").append(callIdVal).append("\r\n");
        if (cseqVal != null) sb.append("CSeq: ").append(cseqVal).append("\r\n");
        sb.append("Content-Length: 0\r\n\r\n");

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
        Log.d(TAG, "180 Ringing 已发送");
    }

    private void sendSimpleResponse(String request, int statusCode, String reason) throws Exception {
        String via = SipStack.extractHeader(request, "Via");
        String from = SipStack.extractHeader(request, "From");
        String to = SipStack.extractHeader(request, "To");
        String callIdVal = SipStack.extractHeader(request, "Call-ID");
        String cseqVal = SipStack.extractHeader(request, "CSeq");

        String toWithTag = to;
        if (to != null && !to.contains("tag=") && localTag != null) {
            toWithTag = to + ";tag=" + localTag;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SIP/2.0 ").append(statusCode).append(" ").append(reason).append("\r\n");
        if (via != null) sb.append("Via: ").append(via).append("\r\n");
        if (from != null) sb.append("From: ").append(from).append("\r\n");
        sb.append("To: ").append(toWithTag != null ? toWithTag : "").append("\r\n");
        if (callIdVal != null) sb.append("Call-ID: ").append(callIdVal).append("\r\n");
        if (cseqVal != null) sb.append("CSeq: ").append(cseqVal).append("\r\n");
        sb.append("Content-Length: 0\r\n\r\n");

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
    }

    // ===== 工具方法 =====

    private String extractSdpBody(String sipMessage) {
        int idx = sipMessage.indexOf("\r\n\r\n");
        return idx >= 0 ? sipMessage.substring(idx + 4) : "";
    }

    private String extractUsernameFromHeader(String header) {
        if (header == null) return "";
        int start = header.indexOf("sip:");
        if (start < 0) return "";
        start += 4;
        int end = header.indexOf('@', start);
        return end > start ? header.substring(start, end) : "";
    }

    private String extractTagFromHeader(String header) {
        if (header == null) return null;
        int idx = header.indexOf("tag=");
        if (idx < 0) return null;
        int start = idx + 4;
        int end = header.indexOf(';', start);
        if (end < 0) end = header.indexOf('>', start);
        if (end < 0) end = header.length();
        return header.substring(start, end).trim();
    }

    private void clearCallState() {
        currentCallId = null;
        currentRemoteUser = null;
        currentCallType = null;
        remoteTag = null;
        localTag = null;
        inviteBranch = null;
        incomingInviteRaw = null;
        incomingInvitePacket = null;
    }

    /** 分配一个随机 RTP 端口（偶数，10000-60000 范围） */
    public static int allocateRtpPort() {
        Random random = new Random();
        int port = 10000 + random.nextInt(25000);
        return (port % 2 == 0) ? port : port + 1; // RTP 端口必须偶数
    }
}
