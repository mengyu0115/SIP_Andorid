package com.example.myapplication.conference;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;

/**
 * 会议信令 WebSocket 客户端
 * 对应 PC 端 ConferenceWebSocketClient
 *
 * 连接地址：ws://{ip}:{port}/ws/conference?userId={userId}
 * 处理：加入/离开/聊天/状态更新等信令消息
 */
public class ConferenceSignalingClient {

    private static final String TAG = "ConferenceSignaling";

    private WebSocketClient webSocketClient;
    private final String serverUrl;
    private final String userId;
    private final String username;
    private volatile boolean connected = false;
    private SignalingListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SignalingListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onMessage(String type, JSONObject json);
    }

    public ConferenceSignalingClient(String serverIp, int port, String userId, String username) {
        this.serverUrl = "ws://" + serverIp + ":" + port + "/ws/conference?userId=" + userId;
        this.userId = userId;
        this.username = username;
    }

    public void setListener(SignalingListener listener) {
        this.listener = listener;
    }

    public void connect() {
        try {
            URI uri = new URI(serverUrl);
            Log.i(TAG, "Connecting signaling WebSocket: " + uri);

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.i(TAG, "Signaling WebSocket connected");
                    connected = true;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onConnected());
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "Signaling WebSocket closed: code=" + code + ", reason=" + reason);
                    connected = false;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onDisconnected());
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "Signaling WebSocket error: " + ex.getMessage());
                    connected = false;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onError(ex.getMessage()));
                    }
                }
            };

            webSocketClient.setConnectionLostTimeout(0);
            webSocketClient.connect();

        } catch (Exception e) {
            Log.e(TAG, "Connect failed", e);
        }
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            if (listener != null) {
                mainHandler.post(() -> listener.onMessage(type, json));
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle message failed: " + message, e);
        }
    }

    /**
     * 发送加入会议消息
     */
    public void sendJoinConference(long conferenceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "JOIN_CONFERENCE");
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            sendJson(json.toString());
            Log.i(TAG, "Sent JOIN_CONFERENCE: conferenceId=" + conferenceId);
        } catch (Exception e) {
            Log.e(TAG, "Send join failed", e);
        }
    }

    /**
     * 发送离开会议消息
     */
    public void sendLeaveConference(long conferenceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "LEAVE_CONFERENCE");
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            sendJson(json.toString());
            Log.i(TAG, "Sent LEAVE_CONFERENCE: conferenceId=" + conferenceId);
        } catch (Exception e) {
            Log.e(TAG, "Send leave failed", e);
        }
    }

    /**
     * 发送会议聊天消息
     */
    public void sendConferenceMessage(long conferenceId, String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "conference_message");
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            json.put("message", message);
            sendJson(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send message failed", e);
        }
    }

    /**
     * 发送媒体状态更新
     */
    public void sendMediaStatusUpdate(long conferenceId, boolean audioEnabled, boolean videoEnabled, boolean screenSharing) {
        try {
            JSONObject data = new JSONObject();
            data.put("conferenceId", String.valueOf(conferenceId));
            data.put("userId", userId);
            data.put("audioEnabled", audioEnabled);
            data.put("videoEnabled", videoEnabled);
            data.put("screenSharing", screenSharing);

            JSONObject json = new JSONObject();
            json.put("type", "media_status_update");
            json.put("data", data);
            sendJson(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send media status failed", e);
        }
    }

    /**
     * 发送开始屏幕共享通知
     */
    public void sendScreenShareStart(long conferenceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "screen_share_start");
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            sendJson(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send screen share start failed", e);
        }
    }

    /**
     * 发送停止屏幕共享通知
     */
    public void sendScreenShareStop(long conferenceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "screen_share_stop");
            json.put("conferenceId", conferenceId);
            json.put("userId", userId);
            json.put("username", username);
            sendJson(json.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send screen share stop failed", e);
        }
    }

    private void sendJson(String json) {
        if (webSocketClient != null && connected && webSocketClient.isOpen()) {
            webSocketClient.send(json);
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send");
        }
    }

    public boolean isConnected() {
        return connected && webSocketClient != null && webSocketClient.isOpen();
    }

    public void disconnect() {
        try {
            if (webSocketClient != null) {
                webSocketClient.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Disconnect failed", e);
        }
        connected = false;
    }
}
