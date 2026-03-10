package com.example.myapplication.network;

/**
 * 服务器配置
 *
 * 对应 PC 端 SipConfig / HttpClientUtil 中的 baseUrl 配置
 * HTTP 后端：http://<serverIp>:8081
 * SIP 服务器端口：5060
 */
public class ServerConfig {

    /** HTTP 后端端口（对应 application.yml server.port） */
    public static final int HTTP_PORT = 8081;

    /** SIP 服务器端口（对应 application.yml sip.server.port） */
    public static final int SIP_PORT = 5060;

    /** 默认服务器 IP（可由登录界面输入覆盖） */
    public static final String DEFAULT_SERVER_IP = "10.29.209.85";

    // ---- 运行时动态配置（登录后设置） ----
    private static String serverIp = DEFAULT_SERVER_IP;
    private static String authToken = null;
    private static Long currentUserId = null;
    private static String currentUsername = null;
    private static String sipUri = null;
    private static String sipPassword = null;

    /** 获取 HTTP base URL，例如 http://10.129.114.129:8081 */
    public static String getBaseUrl() {
        return "http://" + serverIp + ":" + HTTP_PORT;
    }

    public static String getServerIp() {
        return serverIp;
    }

    public static void setServerIp(String ip) {
        serverIp = ip;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setAuthToken(String token) {
        authToken = token;
    }

    public static Long getCurrentUserId() {
        return currentUserId;
    }

    public static void setCurrentUserId(Long userId) {
        currentUserId = userId;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    public static String getSipUri() {
        return sipUri;
    }

    public static void setSipUri(String uri) {
        sipUri = uri;
    }

    public static String getSipPassword() {
        return sipPassword;
    }

    public static void setSipPassword(String password) {
        sipPassword = password;
    }

    /** 从 sipUri 中提取 SIP 用户名，例如 sip:alice@host -> alice */
    public static String getSipUsername() {
        if (sipUri == null || sipUri.isEmpty()) return null;
        String trimmed = sipUri.startsWith("sip:") ? sipUri.substring(4) : sipUri;
        int atIdx = trimmed.indexOf('@');
        return atIdx > 0 ? trimmed.substring(0, atIdx) : trimmed;
    }

    /** 清除登录状态 */
    public static void clear() {
        authToken = null;
        currentUserId = null;
        currentUsername = null;
        sipUri = null;
        sipPassword = null;
    }
}
