package com.example.myapplication.sip;

import android.util.Log;

import com.example.myapplication.SipMessageReceiver;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SIP MESSAGE 处理器
 *
 * 对应 PC 端 message/SipMessageManager.java：
 * - 发送 SIP MESSAGE（文本/图片/文件/语音/视频）
 * - 接收 SIP MESSAGE 请求 → 回复 200 OK → 转发到 SipMessageReceiver
 * - MESSAGE 401/407 认证重试（对应 PC 端 pendingRequests + handleResponseEvent）
 *
 * 消息格式（与 PC 端 MainController 完全统一）：
 * - 文本：纯文本
 * - 图片：[图片]<url>
 * - 文件：[文件]<filename>|<url>
 * - 语音：[语音]<url>
 * - 视频：[视频]<url>
 */
public class SipMessageHandler implements SipStack.SipPacketHandler {

    private static final String TAG = "SipMessageHandler";

    private final SipStack stack;
    private final String sipServer;
    private final int sipServerPort;
    private String username;
    private String password;

    /** MESSAGE CSeq 递增序号（修复 PC 端对齐：AtomicLong 线程安全递增） */
    private final AtomicLong messageCSeq = new AtomicLong(1);

    /** 待确认消息（Call-ID → 原始请求报文），用于 401 认证重试 */
    private final Map<String, PendingMessage> pendingMessages = new ConcurrentHashMap<>();

    public SipMessageHandler(SipStack stack, String sipServer, int sipServerPort) {
        this.stack = stack;
        this.sipServer = sipServer;
        this.sipServerPort = sipServerPort;
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // ===== 发送消息 =====

    /**
     * 发送 SIP MESSAGE（对应 PC 端 SipMessageManager.sendTextMessage）
     */
    public void sendMessage(String targetUsername, String content) {
        new Thread(() -> {
            try {
                String callId = SipStack.generateCallId();
                long seq = messageCSeq.getAndIncrement();
                String branch = SipStack.generateBranch();
                String fromTag = SipStack.generateTag();
                String localIp = stack.getLocalIp();
                int localPort = stack.getLocalPort();

                byte[] bodyBytes = content.getBytes(StandardCharsets.UTF_8);

                StringBuilder sb = new StringBuilder();
                sb.append("MESSAGE sip:").append(targetUsername).append("@").append(sipServer)
                        .append(" SIP/2.0\r\n");
                sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                        .append(";branch=").append(branch).append("\r\n");
                sb.append("From: <sip:").append(username).append("@").append(sipServer)
                        .append(">;tag=").append(fromTag).append("\r\n");
                sb.append("To: <sip:").append(targetUsername).append("@").append(sipServer)
                        .append(">\r\n");
                sb.append("Call-ID: ").append(callId).append("\r\n");
                sb.append("CSeq: ").append(seq).append(" MESSAGE\r\n");
                sb.append("Max-Forwards: 70\r\n");
                sb.append("Content-Type: text/plain;charset=UTF-8\r\n");
                sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                sb.append("\r\n");
                sb.append(content);

                // 保存待确认（用于 401 重试）
                pendingMessages.put(callId, new PendingMessage(targetUsername, content, seq));

                byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
                stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
                Log.i(TAG, "SIP MESSAGE 已发送 -> " + targetUsername + " (CSeq=" + seq + ")");
            } catch (Exception e) {
                Log.e(TAG, "发送 SIP MESSAGE 失败", e);
            }
        }, "SIP-MsgSend").start();
    }

    // ===== SipPacketHandler 实现 =====

    @Override
    public void handleResponse(int statusCode, String method, String rawMessage, DatagramPacket packet) {
        String callId = SipStack.extractHeader(rawMessage, "Call-ID");

        if (statusCode == 200) {
            // 消息送达确认
            if (callId != null) pendingMessages.remove(callId);
            Log.d(TAG, "MESSAGE 送达确认 200 OK");

        } else if (statusCode == 401 || statusCode == 407) {
            // 认证重试（对应 PC 端 SipMessageManager.handleResponseEvent 中的 401/407 分支）
            handleMessageAuthChallenge(statusCode, rawMessage, callId);

        } else {
            Log.w(TAG, "MESSAGE 响应: " + statusCode);
            if (callId != null) pendingMessages.remove(callId);
        }
    }

    @Override
    public void handleRequest(String method, String rawMessage, DatagramPacket packet) {
        handleIncomingMessage(rawMessage, packet);
    }

    // ===== MESSAGE 401 认证重试 =====

    private void handleMessageAuthChallenge(int statusCode, String rawMessage, String callId) {
        PendingMessage pending = (callId != null) ? pendingMessages.remove(callId) : null;
        if (pending == null) {
            Log.w(TAG, "收到 MESSAGE " + statusCode + " 但无待确认消息");
            return;
        }

        try {
            String authHeaderName = (statusCode == 401) ? "WWW-Authenticate" : "Proxy-Authenticate";
            String authType = (statusCode == 401) ? "Authorization" : "Proxy-Authorization";

            String authLine = SipStack.extractHeader(rawMessage, authHeaderName);
            if (authLine == null) {
                Log.e(TAG, "MESSAGE 认证头缺失");
                return;
            }

            String realm = SipStack.extractDigestParam(authLine, "realm");
            String nonce = SipStack.extractDigestParam(authLine, "nonce");

            // 重新发送带认证的 MESSAGE
            String newCallId = SipStack.generateCallId();
            long seq = messageCSeq.getAndIncrement();
            String branch = SipStack.generateBranch();
            String fromTag = SipStack.generateTag();
            String localIp = stack.getLocalIp();
            int localPort = stack.getLocalPort();

            byte[] bodyBytes = pending.content.getBytes(StandardCharsets.UTF_8);
            String requestUri = "sip:" + pending.targetUsername + "@" + sipServer;

            String ha1 = SipStack.md5(username + ":" + realm + ":" + password);
            String ha2 = SipStack.md5("MESSAGE:" + requestUri);
            String response = SipStack.md5(ha1 + ":" + nonce + ":" + ha2);

            StringBuilder sb = new StringBuilder();
            sb.append("MESSAGE ").append(requestUri).append(" SIP/2.0\r\n");
            sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                    .append(";branch=").append(branch).append("\r\n");
            sb.append("From: <sip:").append(username).append("@").append(sipServer)
                    .append(">;tag=").append(fromTag).append("\r\n");
            sb.append("To: <sip:").append(pending.targetUsername).append("@").append(sipServer)
                    .append(">\r\n");
            sb.append("Call-ID: ").append(newCallId).append("\r\n");
            sb.append("CSeq: ").append(seq).append(" MESSAGE\r\n");
            sb.append("Max-Forwards: 70\r\n");
            sb.append(authType).append(": Digest username=\"").append(username)
                    .append("\",realm=\"").append(realm)
                    .append("\",nonce=\"").append(nonce)
                    .append("\",uri=\"").append(requestUri)
                    .append("\",response=\"").append(response)
                    .append("\",algorithm=MD5\r\n");
            sb.append("Content-Type: text/plain;charset=UTF-8\r\n");
            sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            sb.append("\r\n");
            sb.append(pending.content);

            pendingMessages.put(newCallId, pending);

            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
            Log.i(TAG, "MESSAGE 认证重发 -> " + pending.targetUsername + " (CSeq=" + seq + ")");

        } catch (Exception e) {
            Log.e(TAG, "MESSAGE 认证重试失败", e);
        }
    }

    // ===== 接收 MESSAGE =====

    private void handleIncomingMessage(String msg, DatagramPacket packet) {
        try {
            // 提取发送者
            String fromHeader = SipStack.extractHeader(msg, "From");
            String from = (fromHeader != null) ? fromHeader : "";
            int lt = from.indexOf('<');
            int gt = from.indexOf('>');
            if (lt >= 0 && gt > lt) {
                from = from.substring(lt + 1, gt);
            }

            // 提取消息体
            int bodyStart = msg.indexOf("\r\n\r\n");
            String body = (bodyStart >= 0) ? msg.substring(bodyStart + 4) : "";

            // 发送 200 OK
            send200Ok(msg, packet);

            Log.i(TAG, "收到 SIP MESSAGE from: " + from + ", body: " + body);

            // 路由到 SipMessageReceiver
            SipMessageReceiver.getInstance().onSipMessageReceived(from, body);

        } catch (Exception e) {
            Log.e(TAG, "处理 SIP MESSAGE 失败", e);
        }
    }

    private void send200Ok(String request, DatagramPacket requestPacket) {
        try {
            String via = SipStack.extractHeader(request, "Via");
            String from = SipStack.extractHeader(request, "From");
            String to = SipStack.extractHeader(request, "To");
            String callIdVal = SipStack.extractHeader(request, "Call-ID");
            String cseqVal = SipStack.extractHeader(request, "CSeq");

            StringBuilder sb = new StringBuilder();
            sb.append("SIP/2.0 200 OK\r\n");
            if (via != null)       sb.append("Via: ").append(via).append("\r\n");
            if (from != null)      sb.append("From: ").append(from).append("\r\n");
            if (to != null)        sb.append("To: ").append(to).append("\r\n");
            if (callIdVal != null) sb.append("Call-ID: ").append(callIdVal).append("\r\n");
            if (cseqVal != null)   sb.append("CSeq: ").append(cseqVal).append("\r\n");
            sb.append("Content-Length: 0\r\n\r\n");

            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            stack.send(data, requestPacket.getAddress(), requestPacket.getPort());
        } catch (Exception e) {
            Log.e(TAG, "发送 200 OK 失败", e);
        }
    }

    // ===== 内部类 =====

    private static class PendingMessage {
        final String targetUsername;
        final String content;
        final long originalCSeq;

        PendingMessage(String targetUsername, String content, long originalCSeq) {
            this.targetUsername = targetUsername;
            this.content = content;
            this.originalCSeq = originalCSeq;
        }
    }
}
