package com.sip.client.ui.conference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sip.common.dto.MediaFrameMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 会议媒体WebSocket客户端 - 发送和接收媒体流
 *
 * 功能:
 * 1. 发送视频帧（通过WebSocket）
 * 2. 发送音频帧（通过WebSocket）
 * 3. 发送屏幕共享帧
 * 4. 接收远程媒体帧
 *
 * @author SIP Team
 * @since 2025-12-09
 */
public class ConferenceMediaWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceMediaWebSocketClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient webSocketClient;
    private final String serverUrl;
    private final Long conferenceId;
    private final Long userId;
    private final String username;

    private final AtomicLong videoSequence = new AtomicLong(0);
    private final AtomicLong audioSequence = new AtomicLong(0);
    private final AtomicLong screenSequence = new AtomicLong(0);

    // 帧率控制
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private ScheduledFuture<?> videoSendTask;
    private ScheduledFuture<?> screenSendTask;

    // 媒体帧回调
    private Consumer<MediaFrameMessage> onMediaFrameReceived;

    // 连接状态
    private volatile boolean connected = false;

    public ConferenceMediaWebSocketClient(String serverUrl, Long conferenceId, Long userId, String username) {
        this.serverUrl = serverUrl;
        this.conferenceId = conferenceId;
        this.userId = userId;
        this.username = username;
    }

    /**
     * 连接到WebSocket服务器
     */
    public void connect() {
        try {
            URI uri = new URI(serverUrl + "/ws");
            logger.info("🔌 连接媒体WebSocket: {}", uri);

            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    logger.info("✅ 媒体WebSocket已连接: status={}", handshake.getHttpStatus());
                    connected = true;

                    // 发送加入会议消息
                    sendJoinMessage();
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("❌ 媒体WebSocket已关闭: code={}, reason={}, remote={}",
                        code, reason, remote);
                    connected = false;
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("❌ 媒体WebSocket错误: {}", ex.getMessage());
                    connected = false;
                }
            };

            webSocketClient.connect();

        } catch (Exception e) {
            logger.error("❌ 连接媒体WebSocket失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送加入会议消息
     */
    private void sendJoinMessage() {
        try {
            MediaFrameMessage message = new MediaFrameMessage();
            message.setType("JOIN_CONFERENCE");
            message.setConferenceId(conferenceId);
            message.setUserId(userId);
            message.setUsername(username);

            String json = objectMapper.writeValueAsString(message);
            webSocketClient.send(json);

            logger.info("📤 发送加入会议消息: conferenceId={}, userId={}", conferenceId, userId);
        } catch (Exception e) {
            logger.error("❌ 发送加入消息失败: {}", e.getMessage());
        }
    }

    /**
     * 开始发送视频帧
     */
    public void startVideoSending(Callable<BufferedImage> frameProvider) {
        if (videoSendTask != null) {
            logger.warn("⚠️  视频发送已启动");
            return;
        }

        // 10 FPS (每100ms一帧)
        videoSendTask = scheduler.scheduleAtFixedRate(() -> {
            if (!connected) return;

            try {
                BufferedImage frame = frameProvider.call();
                if (frame != null) {
                    sendVideoFrame(frame);
                }
            } catch (Exception e) {
                logger.error("❌ 发送视频帧失败: {}", e.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        logger.info("🎥 开始发送视频帧: 10 FPS");
    }

    /**
     * 停止发送视频帧
     */
    public void stopVideoSending() {
        if (videoSendTask != null) {
            videoSendTask.cancel(false);
            videoSendTask = null;
            logger.info("⏸️  停止发送视频帧");
        }
    }

    /**
     * 开始发送屏幕共享帧
     */
    public void startScreenSending(Callable<BufferedImage> frameProvider) {
        if (screenSendTask != null) {
            logger.warn("⚠️  屏幕共享已启动");
            return;
        }

        // 5 FPS (每200ms一帧)
        screenSendTask = scheduler.scheduleAtFixedRate(() -> {
            if (!connected) return;

            try {
                BufferedImage frame = frameProvider.call();
                if (frame != null) {
                    sendScreenFrame(frame);
                }
            } catch (Exception e) {
                logger.error("❌ 发送屏幕帧失败: {}", e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        logger.info("🖥️  开始发送屏幕共享: 5 FPS");
    }

    /**
     * 停止发送屏幕共享帧
     */
    public void stopScreenSending() {
        if (screenSendTask != null) {
            screenSendTask.cancel(false);
            screenSendTask = null;
            logger.info("⏸️  停止发送屏幕共享");
        }
    }

    /**
     * 发送音频帧
     *
     * @param audioData 音频数据（已Base64编码）
     * @param length 音频数据长度
     */
    public void sendAudioFrame(String audioData, int length) {
        if (!connected) {
            logger.warn("⚠️  WebSocket未连接，无法发送音频帧");
            return;
        }

        try {
            MediaFrameMessage message = new MediaFrameMessage();
            message.setType("MEDIA_FRAME");
            message.setConferenceId(conferenceId);
            message.setUserId(userId);
            message.setUsername(username);
            message.setMediaType("AUDIO");
            message.setFrameData(audioData);
            message.setDataLength(length);
            message.setSequence(audioSequence.incrementAndGet());

            String json = objectMapper.writeValueAsString(message);
            webSocketClient.send(json);

            // 降低日志频率 (每100帧记录一次)
            if (audioSequence.get() % 100 == 0) {
                logger.debug("📤 发送音频帧: seq={}, length={} bytes",
                    audioSequence.get(), length);
            }
        } catch (Exception e) {
            logger.error("❌ 发送音频帧失败: {}", e.getMessage());
        }
    }

    /**
     * 发送视频帧（直接发送BufferedImage）
     *
     * @param frame 视频帧图像
     */
    public void sendVideoFrameDirect(BufferedImage frame) {
        if (!connected) {
            logger.warn("⚠️  WebSocket未连接，无法发送视频帧");
            return;
        }

        try {
            sendVideoFrame(frame);
        } catch (Exception e) {
            logger.error("❌ 发送视频帧失败: {}", e.getMessage());
        }
    }

    /**
     * 发送通用消息（支持原始JSON字符串）
     *
     * @param jsonMessage JSON格式的消息
     */
    public void sendMessage(String jsonMessage) {
        if (!connected) {
            logger.warn("⚠️  WebSocket未连接，无法发送消息");
            return;
        }

        try {
            webSocketClient.send(jsonMessage);
            logger.trace("📤 发送消息: {}", jsonMessage.length() > 100 ?
                jsonMessage.substring(0, 100) + "..." : jsonMessage);
        } catch (Exception e) {
            logger.error("❌ 发送消息失败: {}", e.getMessage());
        }
    }

    /**
     * 发送视频帧
     */
    private void sendVideoFrame(BufferedImage frame) {
        try {
            String base64Data = imageToBase64(frame, "jpg", 0.7f);

            MediaFrameMessage message = new MediaFrameMessage();
            message.setType("MEDIA_FRAME");
            message.setConferenceId(conferenceId);
            message.setUserId(userId);
            message.setUsername(username);
            message.setMediaType("VIDEO");
            message.setFrameData(base64Data);
            message.setWidth(frame.getWidth());
            message.setHeight(frame.getHeight());
            message.setSequence(videoSequence.incrementAndGet());

            String json = objectMapper.writeValueAsString(message);
            webSocketClient.send(json);

            // 降低日志频率
            if (videoSequence.get() % 100 == 0) {
                logger.debug("📤 发送视频帧: seq={}, size={} KB",
                    videoSequence.get(), base64Data.length() / 1024);
            }
        } catch (Exception e) {
            logger.error("❌ 发送视频帧失败: {}", e.getMessage());
        }
    }

    /**
     * 发送屏幕共享帧
     */
    private void sendScreenFrame(BufferedImage frame) {
        try {
            String base64Data = imageToBase64(frame, "jpg", 0.6f);

            MediaFrameMessage message = new MediaFrameMessage();
            message.setType("MEDIA_FRAME");
            message.setConferenceId(conferenceId);
            message.setUserId(userId);
            message.setUsername(username);
            message.setMediaType("SCREEN");
            message.setFrameData(base64Data);
            message.setWidth(frame.getWidth());
            message.setHeight(frame.getHeight());
            message.setSequence(screenSequence.incrementAndGet());

            String json = objectMapper.writeValueAsString(message);
            webSocketClient.send(json);

            // 降低日志频率
            if (screenSequence.get() % 50 == 0) {
                logger.debug("📤 发送屏幕帧: seq={}, size={} KB",
                    screenSequence.get(), base64Data.length() / 1024);
            }
        } catch (Exception e) {
            logger.error("❌ 发送屏幕帧失败: {}", e.getMessage());
        }
    }

    /**
     * 图像转Base64
     */
    private String imageToBase64(BufferedImage image, String format, float quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 压缩图像
        BufferedImage resized = resizeImage(image, 320, 240); // 降低分辨率
        ImageIO.write(resized, format, baos);

        byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 缩放图像
     */
    private BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }

    /**
     * 处理接收到的消息
     */
    private void handleIncomingMessage(String message) {
        try {
            MediaFrameMessage frameMessage = objectMapper.readValue(message, MediaFrameMessage.class);

            if ("MEDIA_FRAME".equals(frameMessage.getType())) {
                if (onMediaFrameReceived != null) {
                    onMediaFrameReceived.accept(frameMessage);
                }
            }
        } catch (Exception e) {
            logger.error("❌ 处理接收消息失败: {}", e.getMessage());
        }
    }

    /**
     * 设置媒体帧接收回调
     */
    public void setOnMediaFrameReceived(Consumer<MediaFrameMessage> callback) {
        this.onMediaFrameReceived = callback;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            stopVideoSending();
            stopScreenSending();

            if (webSocketClient != null && connected) {
                // 发送离开消息
                MediaFrameMessage message = new MediaFrameMessage();
                message.setType("LEAVE_CONFERENCE");
                message.setConferenceId(conferenceId);
                message.setUserId(userId);

                String json = objectMapper.writeValueAsString(message);
                webSocketClient.send(json);

                webSocketClient.close();
                logger.info("✅ 媒体WebSocket已断开");
            }

            scheduler.shutdownNow();

        } catch (Exception e) {
            logger.error("❌ 断开媒体WebSocket失败: {}", e.getMessage());
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected && webSocketClient != null && webSocketClient.isOpen();
    }
}
