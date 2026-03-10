package com.example.myapplication.network;

import android.util.Log;

import com.example.myapplication.model.ApiResult;
import com.example.myapplication.model.LoginData;
import com.example.myapplication.model.UserInfo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 客户端工具类
 *
 * 对应 PC 端 HttpClientUtil，使用 Android 原生 HttpURLConnection 实现。
 * 所有请求均在调用方的线程中执行（应在子线程或 ExecutorService 中调用）。
 *
 * 请求格式：Content-Type: application/json
 * 鉴权方式：Authorization: Bearer <token>（对应 PC 端 TokenInterceptor）
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 30_000;

    // ===== 用户模块 =====

    /**
     * 用户登录
     * POST /api/user/login
     * Body: {"username": "xxx", "password": "xxx"}
     *
     * 对应 PC 端 LoginController.callLoginAPI()
     */
    public static ApiResult<LoginData> login(String serverIp, String username, String password) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            String rawJson = postJson(
                    "http://" + serverIp + ":" + ServerConfig.HTTP_PORT + "/api/user/login",
                    body.toString(),
                    null  // 登录不需要 token
            );

            return parseLoginResult(rawJson);

        } catch (Exception e) {
            Log.e(TAG, "login failed", e);
            return null;
        }
    }

    /**
     * 注册在线状态
     * POST /api/online/login
     * Body: {"userId": 1, "deviceInfo": "Android"}
     * Header: Authorization: Bearer <token>
     *
     * 对应 PC 端 LoginController.registerOnlineStatus()
     */
    public static boolean registerOnlineStatus(Long userId, String token) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("deviceInfo", "Android");

            String raw = postJson(
                    ServerConfig.getBaseUrl() + "/api/online/login",
                    body.toString(),
                    token
            );

            if (raw == null) return false;
            JSONObject json = new JSONObject(raw);
            return json.optInt("code", -1) == 200;

        } catch (Exception e) {
            Log.e(TAG, "registerOnlineStatus failed", e);
            return false;
        }
    }

    /**
     * 用户登出（在线状态）
     * POST /api/online/logout/{userId}
     * Header: Authorization: Bearer <token>
     */
    public static void logoutOnlineStatus(Long userId) {
        try {
            postJson(
                    ServerConfig.getBaseUrl() + "/api/online/logout/" + userId,
                    "{}",
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "logoutOnlineStatus failed", e);
        }
    }

    /**
     * 心跳保活
     * POST /api/online/heartbeat/{userId}
     * Header: Authorization: Bearer <token>
     */
    public static void heartbeat(Long userId) {
        try {
            postJson(
                    ServerConfig.getBaseUrl() + "/api/online/heartbeat/" + userId,
                    "{}",
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "heartbeat failed", e);
        }
    }

    /**
     * 获取离线消息
     * GET /api/message/offline/{userId}
     * Header: Authorization: Bearer <token>
     */
    public static String getOfflineMessages(Long userId) {
        try {
            return getJson(
                    ServerConfig.getBaseUrl() + "/api/message/offline/" + userId,
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "getOfflineMessages failed", e);
            return null;
        }
    }

    /**
     * 获取聊天记录
     * GET /api/message/history?userId1=x&userId2=y&limit=50
     * Header: Authorization: Bearer <token>
     */
    public static String getChatHistory(Long userId1, Long userId2, int limit) {
        try {
            String path = "/api/message/history?userId1=" + userId1
                    + "&userId2=" + userId2 + "&limit=" + limit;
            return getJson(ServerConfig.getBaseUrl() + path, ServerConfig.getAuthToken());
        } catch (Exception e) {
            Log.e(TAG, "getChatHistory failed", e);
            return null;
        }
    }

    /**
     * 保存消息到数据库
     * POST /api/message/send
     * Body: Message JSON 对象
     * Header: Authorization: Bearer <token>
     */
    public static String sendMessageToServer(String messageJson) {
        try {
            return postJson(
                    ServerConfig.getBaseUrl() + "/api/message/send",
                    messageJson,
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "sendMessageToServer failed", e);
            return null;
        }
    }

    /**
     * 获取所有在线用户
     * GET /api/online/users
     * Header: Authorization: Bearer <token>
     */
    public static String getOnlineUsers() {
        try {
            return getJson(
                    ServerConfig.getBaseUrl() + "/api/online/users",
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "getOnlineUsers failed", e);
            return null;
        }
    }

    /**
     * 获取所有用户列表
     * GET /api/user/list
     * Header: Authorization: Bearer <token>
     */
    public static String getUserList() {
        try {
            return getJson(
                    ServerConfig.getBaseUrl() + "/api/user/list",
                    ServerConfig.getAuthToken()
            );
        } catch (Exception e) {
            Log.e(TAG, "getUserList failed", e);
            return null;
        }
    }

    // ===== 底层 HTTP 工具 =====

    /**
     * 发起 POST JSON 请求，返回原始响应字符串
     */
    public static String postJson(String urlStr, String jsonBody, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) return null;
            String response = new String(readAllBytes(is), StandardCharsets.UTF_8);
            Log.d(TAG, "POST " + urlStr + " -> " + code + " " + response);
            return response;

        } finally {
            conn.disconnect();
        }
    }

    /**
     * 发起 GET 请求，返回原始响应字符串
     */
    public static String getJson(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) return null;
            String response = new String(readAllBytes(is), StandardCharsets.UTF_8);
            Log.d(TAG, "GET " + urlStr + " -> " + code);
            return response;

        } finally {
            conn.disconnect();
        }
    }

    // ===== 响应解析 =====

    /** 兼容所有 Android 版本的流读取工具 */
    public static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    /**
     * 解析登录接口响应
     *
     * 服务端格式：
     * { "code": 200, "message": "登录成功",
     *   "data": { "token": "...", "expiresIn": 86400,
     *             "user": { "id": 1, "username": "alice",
     *                       "sipUri": "sip:alice@host",
     *                       "sipPassword": "xxx", ... } } }
     */
    private static ApiResult<LoginData> parseLoginResult(String rawJson) {
        if (rawJson == null) return null;
        try {
            JSONObject root = new JSONObject(rawJson);
            ApiResult<LoginData> result = new ApiResult<>();
            result.setCode(root.optInt("code", -1));
            result.setMessage(root.optString("message", ""));

            if (result.isSuccess() && root.has("data")) {
                JSONObject dataObj = root.getJSONObject("data");
                LoginData loginData = new LoginData();
                loginData.setToken(dataObj.optString("token", null));
                loginData.setExpiresIn(dataObj.optInt("expiresIn", 86400));

                if (dataObj.has("user")) {
                    JSONObject userObj = dataObj.getJSONObject("user");
                    UserInfo user = new UserInfo();
                    user.setId(userObj.optLong("id", -1L));
                    user.setUsername(userObj.optString("username", null));
                    user.setNickname(userObj.optString("nickname", null));
                    user.setAvatar(userObj.optString("avatar", null));
                    user.setStatus(userObj.optInt("status", 0));
                    user.setSipUri(userObj.optString("sipUri", null));
                    user.setSipPassword(userObj.optString("sipPassword", null));
                    loginData.setUser(user);
                }
                result.setData(loginData);
            }
            return result;

        } catch (Exception e) {
            Log.e(TAG, "parseLoginResult failed", e);
            return null;
        }
    }
}
