package com.sip.client.ui.conference;

import com.sip.client.media.MediaManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议媒体处理器 - 仅屏幕共享版本
 *
 * 功能：
 * - 屏幕共享捕获和WebSocket传输
 * - 远程屏幕共享接收和显示
 *
 * @author SIP Team
 * @version 4.0 - 简化版（仅屏幕共享）
 */
public class ConferenceMediaHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceMediaHandler.class);

    // ========== 媒体管理 ==========
    private MediaManager mediaManager;

    // ========== 屏幕捕获 ==========
    private Robot screenCaptureRobot;
    private Rectangle screenRect;
    private Timer screenTimer;

    // ========== 视频渲染回调 ==========
    private VideoFrameCallback screenShareCallback;
    private Map<String, VideoFrameCallback> remoteScreenCallbacks = new ConcurrentHashMap<>();

    // ========== WebSocket客户端 ==========
    private ConferenceWebSocketClient webSocketClient;
    private String currentUserId;

    /**
     * 视频帧回调接口
     */
    public interface VideoFrameCallback {
        void onFrame(Image frame);
    }

    /**
     * 构造函数
     */
    public ConferenceMediaHandler() {
        logger.info("创建 ConferenceMediaHandler (仅屏幕共享模式)");
        this.mediaManager = MediaManager.getInstance();
    }

    /**
     * 初始化媒体处理器
     */
    public void initialize() throws Exception {
        logger.info("初始化 ConferenceMediaHandler");

        try {
            if (mediaManager != null && !mediaManager.isInitialized()) {
                mediaManager.initialize();
            }
        } catch (Exception e) {
            logger.warn("MediaManager 初始化失败: {}", e.getMessage());
        }

        logger.info("ConferenceMediaHandler 初始化完成");
    }

    /**
     * 设置WebSocket客户端
     */
    public void setWebSocketClient(ConferenceWebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    /**
     * 设置当前用户ID
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    // ========== 屏幕共享处理 ==========

    /**
     * 启动屏幕共享
     */
    public void startScreenShare(VideoFrameCallback callback) {
        try {
            if (screenCaptureRobot != null) {
                logger.warn("屏幕共享已经在运行");
                return;
            }

            this.screenShareCallback = callback;

            // 创建 Robot 用于屏幕捕获
            screenCaptureRobot = new Robot();

            // 获取屏幕尺寸
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            screenRect = new Rectangle(screenSize);

            logger.info("屏幕尺寸: {}x{}", screenSize.width, screenSize.height);

            // 启动定时器捕获屏幕（5 FPS）
            screenTimer = new Timer("ScreenCapture", true);
            screenTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    captureAndSendScreenFrame();
                }
            }, 0, 200); // 5 FPS

            logger.info("屏幕共享已启动");

        } catch (Exception e) {
            logger.error("启动屏幕共享失败", e);
            throw new RuntimeException("启动屏幕共享失败: " + e.getMessage(), e);
        }
    }

    /**
     * 捕获并发送屏幕画面
     */
    private void captureAndSendScreenFrame() {
        try {
            if (screenCaptureRobot != null) {
                BufferedImage screenCapture = screenCaptureRobot.createScreenCapture(screenRect);
                if (screenCapture != null) {
                    // 显示在本地界面
                    if (screenShareCallback != null) {
                        Image fxImage = SwingFXUtils.toFXImage(screenCapture, null);
                        Platform.runLater(() -> screenShareCallback.onFrame(fxImage));
                    }

                    // 通过WebSocket发送JPEG数据
                    if (webSocketClient != null && webSocketClient.isOpen()) {
                        sendScreenFrameViaWebSocket(screenCapture);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("捕获屏幕画面失败", e);
        }
    }

    /**
     * 通过WebSocket发送屏幕帧
     */
    private void sendScreenFrameViaWebSocket(BufferedImage image) {
        try {
            // 缩小图片尺寸（缩放到50%，减少消息大小）
            int targetWidth = image.getWidth() / 2;
            int targetHeight = image.getHeight() / 2;

            BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = scaledImage.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                               java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            // 编码为JPEG（降低质量到30%以减少大小）
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            javax.imageio.ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("JPEG").next();
            javax.imageio.ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
            jpgWriteParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            jpgWriteParam.setCompressionQuality(0.3f); // 30% 质量（降低消息大小）

            jpgWriter.setOutput(ImageIO.createImageOutputStream(outputStream));
            jpgWriter.write(null, new javax.imageio.IIOImage(scaledImage, null, null), jpgWriteParam);
            jpgWriter.dispose();

            byte[] jpegData = outputStream.toByteArray();

            // Base64编码
            String base64Data = Base64.getEncoder().encodeToString(jpegData);

            // 发送WebSocket消息
            String json = String.format(
                "{\"type\":\"screen_frame\",\"data\":{\"userId\":\"%s\",\"frame\":\"%s\",\"width\":%d,\"height\":%d}}",
                currentUserId, base64Data, targetWidth, targetHeight
            );

            webSocketClient.sendJson(json);

            logger.trace("发送屏幕帧: 原始尺寸={}x{}, 压缩后尺寸={}x{}, JPEG大小={} bytes, Base64大小={} bytes",
                        image.getWidth(), image.getHeight(), targetWidth, targetHeight, jpegData.length, base64Data.length());

        } catch (Exception e) {
            logger.error("通过WebSocket发送屏幕帧失败", e);
        }
    }

    /**
     * 停止屏幕共享
     */
    public void stopScreenShare() {
        if (screenTimer != null) {
            screenTimer.cancel();
            screenTimer = null;
        }

        screenCaptureRobot = null;
        screenRect = null;
        screenShareCallback = null;

        logger.info("屏幕共享已停止");
    }

    // ========== 远程屏幕接收 ==========

    /**
     * 注册远程屏幕共享回调
     */
    public void registerRemoteScreenCallback(String participantId, VideoFrameCallback callback) {
        remoteScreenCallbacks.put(participantId, callback);
        logger.info("注册远程屏幕回调: {}", participantId);
    }

    /**
     * 取消注册远程屏幕共享回调
     */
    public void unregisterRemoteScreenCallback(String participantId) {
        remoteScreenCallbacks.remove(participantId);
        logger.info("取消远程屏幕回调: {}", participantId);
    }

    /**
     * 处理接收到的远程屏幕帧
     */
    public void handleRemoteScreenFrame(String userId, String base64Data, int width, int height) {
        try {
            logger.info("开始处理远程屏幕帧: userId={}, base64长度={}, 尺寸={}x{}",
                       userId, base64Data.length(), width, height);

            // Base64解码
            byte[] jpegData = Base64.getDecoder().decode(base64Data);
            logger.info("Base64解码成功: JPEG大小={} bytes", jpegData.length);

            // JPEG解码
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(jpegData);
            BufferedImage bufferedImage = ImageIO.read(inputStream);

            if (bufferedImage != null) {
                logger.info("JPEG解码成功: 图片尺寸={}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());

                // 转换为JavaFX Image
                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                // 调用回调显示
                String screenShareId = userId + "_screen";
                VideoFrameCallback callback = remoteScreenCallbacks.get(screenShareId);
                if (callback != null) {
                    Platform.runLater(() -> {
                        callback.onFrame(fxImage);
                        logger.debug("已显示远程屏幕帧: userId={}", userId);
                    });
                } else {
                    logger.warn("未找到屏幕共享回调: screenShareId={}", screenShareId);
                }
            } else {
                logger.error("JPEG解码失败：bufferedImage为null");
            }

        } catch (Exception e) {
            logger.error("处理远程屏幕帧失败: userId={}", userId, e);
        }
    }

    // ========== 音频处理 (保留但不实现) ==========

    public void startAudio(Map<String, Object> remoteMediaInfo) {
        logger.info("音频功能暂不支持");
    }

    public void stopAudio() {
        logger.info("音频功能暂不支持");
    }

    // ========== 清理 ==========

    /**
     * 清理所有资源
     */
    public void cleanup() {
        logger.info("清理 ConferenceMediaHandler");

        stopScreenShare();
        remoteScreenCallbacks.clear();

        logger.info("ConferenceMediaHandler 已清理");
    }
}
