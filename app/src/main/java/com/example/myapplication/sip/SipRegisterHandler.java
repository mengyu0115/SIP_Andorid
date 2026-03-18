package com.example.myapplication.sip;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SIP REGISTER 处理器
 *
 * 对应 PC 端 register/SipRegisterManager.java：
 * - REGISTER 流程（无认证 → 401/407 Digest → 200 OK）
 * - 心跳保活（Re-REGISTER，周期 expiresTime-60s）
 * - 注销（Expires=0）
 * - Expires 协商（读取服务器返回值）
 *
 * 对应 PC 端 register/SipAuthHandler.java：
 * - Digest MD5 计算内联实现
 */
public class SipRegisterHandler implements SipStack.SipPacketHandler {

    private static final String TAG = "SipRegisterHandler";

    private final SipStack stack;
    private final String sipServer;
    private final int sipServerPort;

    private String username;
    private String password;
    private String callId;
    private long cseq = 1;
    private int expiresTime = 3600;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private Timer keepAliveTimer;
    private RegisterCallback callback;

    public interface RegisterCallback {
        void onRegisterSuccess();
        void onRegisterFailed(String reason);
        void onUnregisterSuccess();
    }

    public SipRegisterHandler(SipStack stack, String sipServer, int sipServerPort) {
        this.stack = stack;
        this.sipServer = sipServer;
        this.sipServerPort = sipServerPort;
        this.callId = SipStack.generateCallId();
    }

    public void setCallback(RegisterCallback callback) {
        this.callback = callback;
    }

    // ===== 注册 =====

    public void register(String username, String password) {
        this.username = username;
        this.password = password;
        try {
            Log.i(TAG, "开始 SIP 注册: " + username + "@" + sipServer);
            sendRegister(null, null, null);
        } catch (Exception e) {
            Log.e(TAG, "SIP 注册发送失败", e);
            if (callback != null) callback.onRegisterFailed("注册失败: " + e.getMessage());
        }
    }

    // ===== 注销 =====

    public void unregister() {
        try {
            if (!registered.get()) return;
            stopKeepAlive();
            expiresTime = 0;
            sendRegister(null, null, null);
            registered.set(false);
        } catch (Exception e) {
            Log.e(TAG, "注销失败", e);
        }
    }

    public boolean isRegistered() { return registered.get(); }
    public String getUsername() { return username; }

    // ===== SipPacketHandler 实现 =====

    @Override
    public void handleResponse(int statusCode, String method, String rawMessage, DatagramPacket packet) {
        if (statusCode == 200) {
            handleRegisterSuccess(rawMessage);
        } else if (statusCode == 401) {
            handleAuthChallenge(rawMessage, "WWW-Authenticate", "Authorization");
        } else if (statusCode == 407) {
            handleAuthChallenge(rawMessage, "Proxy-Authenticate", "Proxy-Authorization");
        } else {
            Log.e(TAG, "注册失败: " + statusCode);
            if (callback != null) callback.onRegisterFailed("注册失败: " + statusCode);
        }
    }

    @Override
    public void handleRequest(String method, String rawMessage, DatagramPacket packet) {
        // REGISTER handler 不处理请求
    }

    // ===== 内部方法 =====

    private void sendRegister(String realm, String nonce, String authType) throws Exception {
        long seq = cseq++;
        String localIp = stack.getLocalIp();
        int localPort = stack.getLocalPort();
        String contactUri = "sip:" + username + "@" + localIp + ":" + localPort;

        StringBuilder sb = new StringBuilder();
        sb.append("REGISTER sip:").append(sipServer).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localPort)
                .append(";branch=").append(SipStack.generateBranch()).append("\r\n");
        sb.append("From: <sip:").append(username).append("@").append(sipServer)
                .append(">;tag=").append(System.currentTimeMillis()).append("\r\n");
        sb.append("To: <sip:").append(username).append("@").append(sipServer).append(">\r\n");
        sb.append("Call-ID: ").append(callId).append("\r\n");
        sb.append("CSeq: ").append(seq).append(" REGISTER\r\n");
        sb.append("Max-Forwards: 70\r\n");
        sb.append("Contact: <").append(contactUri).append(">\r\n");
        sb.append("Expires: ").append(expiresTime).append("\r\n");

        if (realm != null && nonce != null && authType != null) {
            String ha1 = SipStack.md5(username + ":" + realm + ":" + password);
            String ha2 = SipStack.md5("REGISTER:sip:" + sipServer);
            String response = SipStack.md5(ha1 + ":" + nonce + ":" + ha2);
            sb.append(authType).append(": Digest username=\"").append(username)
                    .append("\",realm=\"").append(realm)
                    .append("\",nonce=\"").append(nonce)
                    .append("\",uri=\"sip:").append(sipServer)
                    .append("\",response=\"").append(response)
                    .append("\",algorithm=MD5\r\n");
        }

        sb.append("Content-Length: 0\r\n\r\n");

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        stack.send(data, InetAddress.getByName(sipServer), sipServerPort);
        Log.i(TAG, "REGISTER 已发送 (CSeq=" + seq + ", auth=" + (realm != null) + ")");
    }

    private void handleRegisterSuccess(String msg) {
        if (expiresTime == 0) {
            registered.set(false);
            Log.i(TAG, "注销成功");
            if (callback != null) callback.onUnregisterSuccess();
            return;
        }

        String expiresStr = SipStack.extractHeader(msg, "Expires");
        if (expiresStr != null) {
            try {
                int serverExpires = Integer.parseInt(expiresStr.trim());
                if (serverExpires > 0 && serverExpires != expiresTime) {
                    Log.w(TAG, "服务器返回 Expires=" + serverExpires + "，更新本地值");
                    expiresTime = serverExpires;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 同时检查 Contact 头中的 expires 参数（对应 PC 端 MSS 修复双路径）
        String contactHeader = SipStack.extractHeader(msg, "Contact");
        if (contactHeader != null) {
            String contactExpires = SipStack.extractDigestParam(contactHeader, "expires");
            if (contactExpires != null) {
                try {
                    int ce = Integer.parseInt(contactExpires.trim());
                    if (ce > 0 && ce != expiresTime) {
                        Log.w(TAG, "Contact expires=" + ce + "，更新本地值");
                        expiresTime = ce;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        registered.set(true);
        Log.i(TAG, "注册成功: " + username + "@" + sipServer + ", Expires=" + expiresTime + "s");
        startKeepAlive();
        if (callback != null) callback.onRegisterSuccess();
    }

    private void handleAuthChallenge(String msg, String authHeader, String authType) {
        try {
            String authLine = SipStack.extractHeader(msg, authHeader);
            if (authLine == null) {
                if (callback != null) callback.onRegisterFailed("认证头缺失");
                return;
            }
            String realm = SipStack.extractDigestParam(authLine, "realm");
            String nonce = SipStack.extractDigestParam(authLine, "nonce");
            Log.i(TAG, "收到认证质询, realm=" + realm + ", nonce=" + nonce);
            sendRegister(realm, nonce, authType);
        } catch (Exception e) {
            Log.e(TAG, "处理认证质询失败", e);
            if (callback != null) callback.onRegisterFailed("认证失败: " + e.getMessage());
        }
    }

    private void startKeepAlive() {
        stopKeepAlive();
        long period = Math.max((expiresTime - 60) * 1000L, 30_000L);
        keepAliveTimer = new Timer("SIP-KeepAlive", true);
        keepAliveTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "心跳保活 Re-REGISTER");
                    sendRegister(null, null, null);
                } catch (Exception e) {
                    Log.e(TAG, "心跳保活失败", e);
                }
            }
        }, period, period);
        Log.i(TAG, "心跳保活已启动，周期: " + (period / 1000) + "秒");
    }

    private void stopKeepAlive() {
        if (keepAliveTimer != null) {
            keepAliveTimer.cancel();
            keepAliveTimer = null;
        }
    }

    public void shutdown() {
        stopKeepAlive();
    }
}
