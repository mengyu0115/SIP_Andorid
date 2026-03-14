package com.example.myapplication.network;

import com.example.myapplication.BuildConfig;

/**
 * 服务器配置
 *
 * 对应 PC 端 SipConfig / HttpClientUtil 中的 baseUrl 配置
 * HTTP 后端：http://<serverIp>:8081
 * SIP 服务器端口：5060
 *
 * ===== IP配置统一管理 =====
 * 服务器IP从 ../../config.properties 读取，编译时生成到 BuildConfig
 * 修改IP只需编辑 config.properties 文件，然后 Sync Gradle 并 Rebuild 即可
 */
public class ServerConfig {

    /** 从共享配置文件读取的服务器IP（编译时固定） */
    public static String DEFAULT_SERVER_IP = BuildConfig.SERVER_IP;

    /**
     * 获取默认服务器IP（模拟器自动使用 10.0.2.2）
     */
    public static String getDefaultServerIp() {
        // 检测是否在模拟器中运行
        if (isRunningOnEmulator()) {
            return "10.0.2.2";  // 模拟器使用特殊IP
        }
        return DEFAULT_SERVER_IP;  // 真机使用配置的IP
    }

    /**
     * 检测是否运行在Android模拟器中
     */
    private static boolean isRunningOnEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.MANUFACTURER.contains("Genymotion")
            || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(android.os.Build.PRODUCT);
    }

    /** HTTP 后端端口（从共享配置读取） */
    public static final int HTTP_PORT = BuildConfig.HTTP_PORT;

    /** SIP 服务器端口（从共享配置读取） */
    public static final int SIP_PORT = BuildConfig.SIP_PORT;

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
