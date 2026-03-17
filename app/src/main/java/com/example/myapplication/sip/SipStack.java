package com.example.myapplication.sip;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SIP UDP 基础设施层
 *
 * 对应 PC 端 core/SipManager.java：
 * - 管理 DatagramSocket 生命周期（绑定/关闭）
 * - 提供 UDP 收发原语（send / receiveLoop）
 * - 将收到的 SIP 报文路由到已注册的 Handler
 * - 提供共享工具方法（extractHeader, md5, generate*）
 *
 * 架构角色：
 *   SipStack（基础设施） → SipRegisterHandler（REGISTER）
 *                        → SipMessageHandler（MESSAGE）
 *                        → SipCallHandler（INVITE，未来扩展）
 */
public class SipStack {

    private static final String TAG = "SipStack";

    // ===== 单例 =====
    private static volatile SipStack instance;

    public static SipStack getInstance() {
        if (instance == null) {
            synchronized (SipStack.class) {
                if (instance == null) {
                    instance = new SipStack();
                }
            }
        }
        return instance;
    }

    // ===== 状态 =====
    private DatagramSocket socket;
    private String localIp;
    private int localPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiveThread;

    // ===== Handler 注册（对应 PC 端 SipProvider.addSipListener）=====
    private SipPacketHandler registerHandler;
    private SipPacketHandler messageHandler;
    private SipPacketHandler callHandler;

    private SipStack() {}

    /** Handler 接口：各协议子模块实现此接口来接收 SIP 报文 */
    public interface SipPacketHandler {
        /** 处理 SIP 响应（SIP/2.0 xxx） */
        void handleResponse(int statusCode, String method, String rawMessage, DatagramPacket packet);
        /** 处理 SIP 请求（MESSAGE/INVITE/...） */
        void handleRequest(String method, String rawMessage, DatagramPacket packet);
    }

    public void setRegisterHandler(SipPacketHandler handler) { this.registerHandler = handler; }
    public void setMessageHandler(SipPacketHandler handler) { this.messageHandler = handler; }
    public void setCallHandler(SipPacketHandler handler) { this.callHandler = handler; }

    // ===== 初始化 =====

    public synchronized void initialize(String localIp, int localPort) throws Exception {
        if (socket != null && !socket.isClosed()) {
            Log.w(TAG, "SIP 栈已初始化，跳过");
            return;
        }
        this.localIp = localIp;
        this.localPort = localPort;

        socket = new DatagramSocket(localPort, InetAddress.getByName(localIp));
        running.set(true);

        receiveThread = new Thread(this::receiveLoop, "SIP-Receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        Log.i(TAG, "SIP 栈初始化完成: " + localIp + ":" + localPort);
    }

    // ===== 发送 UDP 数据 =====

    public void send(byte[] data, InetAddress address, int port) throws Exception {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException("SIP socket 未初始化");
        }
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }

    // ===== 接收循环 + 路由 =====

    private void receiveLoop() {
        byte[] buf = new byte[65535];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(buf, 0, packet.getLength(), StandardCharsets.UTF_8);
                routeMessage(msg, packet);
            } catch (Exception e) {
                if (running.get()) {
                    Log.e(TAG, "接收 SIP 数据失败", e);
                }
            }
        }
    }

    private void routeMessage(String msg, DatagramPacket packet) {
        try {
            int crIdx = msg.indexOf('\r');
            if (crIdx < 0) return;
            String firstLine = msg.substring(0, crIdx).trim();

            if (firstLine.startsWith("SIP/2.0")) {
                // 响应报文
                int statusCode = Integer.parseInt(firstLine.split(" ")[1]);
                String method = extractCSeqMethod(msg);

                if ("REGISTER".equals(method)) {
                    if (registerHandler != null)
                        registerHandler.handleResponse(statusCode, method, msg, packet);
                } else if ("MESSAGE".equals(method)) {
                    if (messageHandler != null)
                        messageHandler.handleResponse(statusCode, method, msg, packet);
                } else if ("INVITE".equals(method) || "BYE".equals(method)
                        || "ACK".equals(method) || "CANCEL".equals(method)) {
                    if (callHandler != null)
                        callHandler.handleResponse(statusCode, method, msg, packet);
                    else
                        Log.d(TAG, "收到 " + method + " 响应，暂无 handler");
                }
            } else {
                // 请求报文：取方法名
                String method = firstLine.split(" ")[0];
                if ("MESSAGE".equals(method)) {
                    if (messageHandler != null)
                        messageHandler.handleRequest(method, msg, packet);
                } else if ("INVITE".equals(method) || "BYE".equals(method)
                        || "ACK".equals(method) || "CANCEL".equals(method)) {
                    if (callHandler != null)
                        callHandler.handleRequest(method, msg, packet);
                    else
                        Log.d(TAG, "收到 " + method + " 请求，暂无 handler");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "路由 SIP 消息失败", e);
        }
    }

    // ===== 关闭 =====

    public void shutdown() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close(); // 触发 receive() 抛出 SocketException，退出循环
        }
        Log.i(TAG, "SipStack 已关闭");
    }

    // ===== Getter =====

    public String getLocalIp() { return localIp; }
    public int getLocalPort() { return localPort; }
    public boolean isRunning() { return running.get(); }

    // ===== 共享工具方法（供各 Handler 使用）=====

    public static String extractHeader(String msg, String headerName) {
        String prefix = headerName + ":";
        for (String line : msg.split("\r\n")) {
            if (line.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    public static String extractCSeqMethod(String msg) {
        String cseqVal = extractHeader(msg, "CSeq");
        if (cseqVal == null) return "";
        String[] parts = cseqVal.trim().split("\\s+");
        return parts.length > 1 ? parts[1] : "";
    }

    public static String extractDigestParam(String digestLine, String param) {
        String key = param + "=";
        int idx = digestLine.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        if (start < digestLine.length() && digestLine.charAt(start) == '"') {
            int end = digestLine.indexOf('"', start + 1);
            return end > start ? digestLine.substring(start + 1, end) : null;
        }
        int end = digestLine.indexOf(',', start);
        return end > 0 ? digestLine.substring(start, end).trim() : digestLine.substring(start).trim();
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }

    public static String generateCallId() {
        return Long.toHexString(System.nanoTime()) + "@android";
    }

    public static String generateBranch() {
        return "z9hG4bK" + Long.toHexString(System.nanoTime());
    }

    public static String generateTag() {
        return Long.toHexString(System.nanoTime() ^ Thread.currentThread().getId());
    }
}
