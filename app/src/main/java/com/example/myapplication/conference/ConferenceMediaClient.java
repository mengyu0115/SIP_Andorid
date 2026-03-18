package com.example.myapplication.conference;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.MediaFrameMessage;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会议媒体 WebSocket 客户端
 * 对应 PC 端 ConferenceMediaWebSocketClient
 *
 * 连接地址：ws://{ip}:{port}/ws
 * 处理：音频/视频媒体帧的发送和接收
 */
public class ConferenceMediaClient {

    private static final String TAG = "ConferenceMedia";

    private WebSocketClient webSocketClient;
    private final String serverUrl;
    private final Long conferenceId;
    private final Long userId;
    private final String username;
    private volatile boolean connected = false;

    private final AtomicLong videoSequence = new AtomicLong(0);
    private final AtomicLong audioSequence = new AtomicLong(0);
    private final AtomicLong screenSequence = new AtomicLong(0);

    private MediaFrameListener frameListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Dedicated thread pool for decoding received frames (avoids blocking WebSocket thread)
    private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);

    public interface MediaFrameListener {
        void onMediaFrame(MediaFrameMessage frame);
    }

    public ConferenceMediaClient(String serverIp, int port, Long conferenceId, Long userId, String username) {
        this.serverUrl = "ws://" + serverIp + ":" + port + "/ws";
        this.conferenceId = conferenceId;
        this.userId = userId;
        this.username = username;
    }

    public void setFrameListener(MediaFrameListener listener) {
        this.frameListener = listener;
    }

    public void connect() {
        try {
            URI uri = new URI(serverUrl);
            Log.i(TAG, "Connecting media WebSocket: " + uri);

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.i(TAG, "Media WebSocket connected");
                    connected = true;
                    sendJoinMessage();
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.i(TAG, "Media WebSocket closed: code=" + code);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "Media WebSocket error: " + ex.getMessage());
                    connected = false;
                }
            };

            webSocketClient.setConnectionLostTimeout(0);
            webSocketClient.connect();

        } catch (Exception e) {
            Log.e(TAG, "Connect failed", e);
        }
    }

    private void sendJoinMessage() {
        MediaFrameMessage msg = new MediaFrameMessage();
        msg.setType("JOIN_CONFERENCE");
        msg.setConferenceId(conferenceId);
        msg.setUserId(userId);
        msg.setUsername(username);
        sendMessage(msg.toJson().toString());
        Log.i(TAG, "Sent media JOIN_CONFERENCE");
    }

    /**
     * 发送视频帧
     */
    public void sendVideoFrame(String base64Data, int width, int height) {
        if (!connected) return;

        MediaFrameMessage msg = new MediaFrameMessage();
        msg.setType("MEDIA_FRAME");
        msg.setConferenceId(conferenceId);
        msg.setUserId(userId);
        msg.setUsername(username);
        msg.setMediaType("VIDEO");
        msg.setFrameData(base64Data);
        msg.setWidth(width);
        msg.setHeight(height);
        msg.setSequence(videoSequence.incrementAndGet());

        sendMessage(msg.toJson().toString());
    }

    /**
     * 发送音频帧
     */
    public void sendAudioFrame(String base64Data, int length) {
        if (!connected) return;

        MediaFrameMessage msg = new MediaFrameMessage();
        msg.setType("MEDIA_FRAME");
        msg.setConferenceId(conferenceId);
        msg.setUserId(userId);
        msg.setUsername(username);
        msg.setMediaType("AUDIO");
        msg.setFrameData(base64Data);
        msg.setDataLength(length);
        msg.setSequence(audioSequence.incrementAndGet());

        sendMessage(msg.toJson().toString());
    }

    /**
     * 发送屏幕共享帧
     */
    public void sendScreenFrame(String base64Data, int width, int height) {
        if (!connected) return;

        MediaFrameMessage msg = new MediaFrameMessage();
        msg.setType("MEDIA_FRAME");
        msg.setConferenceId(conferenceId);
        msg.setUserId(userId);
        msg.setUsername(username);
        msg.setMediaType("SCREEN");
        msg.setFrameData(base64Data);
        msg.setWidth(width);
        msg.setHeight(height);
        msg.setSequence(screenSequence.incrementAndGet());

        sendMessage(msg.toJson().toString());
    }

    private void handleIncomingMessage(String message) {
        try {
            MediaFrameMessage frame = MediaFrameMessage.fromJson(message);
            if (frame == null) return;

            if ("MEDIA_FRAME".equals(frame.getType())) {
                if (frameListener != null) {
                    // Decode on separate thread to avoid blocking WebSocket reception
                    decodeExecutor.execute(() -> {
                        try {
                            frameListener.onMediaFrame(frame);
                        } catch (Throwable t) {
                            Log.e(TAG, "Process media frame failed (caught to prevent crash)", t);
                        }
                    });
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "Handle incoming message failed", e);
        }
    }

    private void sendMessage(String json) {
        if (webSocketClient != null && connected && webSocketClient.isOpen()) {
            webSocketClient.send(json);
        }
    }

    public boolean isConnected() {
        return connected && webSocketClient != null && webSocketClient.isOpen();
    }

    public void disconnect() {
        try {
            if (webSocketClient != null && connected) {
                // Send leave message
                MediaFrameMessage msg = new MediaFrameMessage();
                msg.setType("LEAVE_CONFERENCE");
                msg.setConferenceId(conferenceId);
                msg.setUserId(userId);
                sendMessage(msg.toJson().toString());

                webSocketClient.close();
                Log.i(TAG, "Media WebSocket disconnected");
            }

            decodeExecutor.shutdownNow();
        } catch (Exception e) {
            Log.e(TAG, "Disconnect failed", e);
        }
        connected = false;
    }
}
