package com.sip.client.media;

import com.sip.client.config.SipConfig;
import com.sip.client.media.audio.AudioRtpHandler;
import com.sip.client.media.video.VideoCapture;
import com.sip.client.media.video.VideoRenderer;
import com.sip.client.media.video.VideoRtpHandler;
import lombok.extern.slf4j.Slf4j;
import java.net.InetAddress;
import java.util.Map;

/**
 * 媒体管理器
 * 统一管理音视频 RTP 传输
 *
 * 功能:
 * 1. 初始化音频/视频 RTP 处理器
 * 2. 启动/停止音视频传输
 * 3. 协调 SIP 呼叫与媒体会话
 * 4. 管理 RTP 端口分配
 * 5. ✅ 预热摄像头，减少首次视频通话延迟
 *
 * @author 成员2
 */
@Slf4j
public class MediaManager {

    // ========== 单例模式 ==========
    private static volatile MediaManager instance;

    public static MediaManager getInstance() {
        if (instance == null) {
            synchronized (MediaManager.class) {
                if (instance == null) {
                    instance = new MediaManager();
                }
            }
        }
        return instance;
    }

    /**
     * 设置RTP端口配置（已废弃，使用SipConfig读取）
     * @deprecated 使用 SipConfig.getRtpAudioPortStart() 代替
     */
    @Deprecated
    public static void setPortConfig(int audioPort, int videoPort) {
        audioPortStart = audioPort;
        videoPortStart = videoPort;
    }

    // ========== 配置值（从SipConfig读取）==========
    private static int audioPortStart;
    private static int videoPortStart;

    // ========== RTP 处理器 ==========
    private AudioRtpHandler audioRtpHandler;
    private VideoRtpHandler videoRtpHandler;

    // ========== 预热的摄像头 ==========
    private VideoCapture prewarmedCamera;  // ✅ 登录时预热的摄像头实例

    // ========== RTP 端口范围 ==========
    private int currentAudioPort;
    private int currentVideoPort;

    // ========== 本地 IP ==========
    private String localIp;

    // ========== 状态 ==========
    private boolean initialized = false;
    private boolean mediaActive = false;

    private MediaManager() {
        // ✅ 端口配置将在 initialize() 方法中从 SipConfig 读取
        // 这样可以确保系统属性已经被加载
    }

    /**
     * 初始化媒体管理器
     */
    public void initialize() throws Exception {
        if (initialized) {
            log.warn("MediaManager 已初始化,跳过");
            return;
        }

        log.info("初始化 MediaManager");

        // ✅ 从 SipConfig 读取 RTP 端口配置（此时系统属性已加载）
        audioPortStart = SipConfig.getRtpAudioPortStart();
        videoPortStart = SipConfig.getRtpVideoPortStart();

        // 初始化端口范围
        this.currentAudioPort = audioPortStart;
        this.currentVideoPort = videoPortStart;

        log.info("MediaManager 端口配置: 音频={}-{}, 视频={}-{}",
                 audioPortStart, SipConfig.getRtpAudioPortEnd(),
                 videoPortStart, SipConfig.getRtpVideoPortEnd());

        // 1. 获取本地 IP
        this.localIp = InetAddress.getLocalHost().getHostAddress();

        log.info("本地 IP: {}", localIp);

        // 2. 初始化音频 RTP 处理器
        audioRtpHandler = new AudioRtpHandler();
        audioRtpHandler.initialize(localIp, getNextAudioPort());

        // 3. 初始化视频 RTP 处理器
        videoRtpHandler = new VideoRtpHandler();
        videoRtpHandler.initialize(localIp, getNextVideoPort());

        initialized = true;
        log.info("MediaManager 初始化成功");
    }

    /**
     * ✅ 预热摄像头（登录后台调用，减少首次视频通话等待时间）
     * 在后台线程中初始化摄像头，但不启动采集，权衡登录速度和通话速度
     */
    public void prewarmCamera() {
        // 在后台线程中预热，避免阻塞主线程
        Thread prewarmThread = new Thread(() -> {
            try {
                log.info("🔥 开始预热摄像头（后台线程）...");
                long startTime = System.currentTimeMillis();

                // 创建VideoCapture实例并预热
                prewarmedCamera = new VideoCapture();
                int cameraDeviceId = SipConfig.getCameraDeviceId();
                prewarmedCamera.prewarm(cameraDeviceId);

                long elapsed = System.currentTimeMillis() - startTime;
                log.info("✅ 摄像头预热完成，耗时: {} ms", elapsed);

            } catch (Exception e) {
                log.warn("⚠️ 摄像头预热失败（不影响正常使用）: {}", e.getMessage());
                prewarmedCamera = null;
            }
        }, "CameraPrewarmThread");

        prewarmThread.setDaemon(true);  // 设置为守护线程
        prewarmThread.start();
    }

    /**
     * ⚡ CRITICAL FIX #9: 获取预热的摄像头实例（供VideoRtpHandler使用）
     *
     * 修复问题：如果预热线程还在执行中（grabber.start()耗时长），
     * 不应该返回未完成预热的摄像头对象，否则会导致 "Could not retrieve frame" 错误
     *
     * @return 预热的VideoCapture实例，如果未预热完成或预热失败则返回null
     */
    public VideoCapture getPrewarmedCamera() {
        // ⚡ 修复：检查预热是否真正完成，而不是只检查对象是否存在
        if (prewarmedCamera != null && prewarmedCamera.isPrewarmed()) {
            return prewarmedCamera;
        }
        return null;  // 预热未完成或失败，返回null让VideoRtpHandler重新初始化
    }

    /**
     * 启动音频通话
     *
     * @param remoteMediaInfo 对方的媒体信息 (从 SDP 中解析)
     */
    public void startAudioCall(Map<String, Object> remoteMediaInfo) {
        try {
            log.info("启动音频通话");

            // 1. 获取对方的 IP 和端口
            String remoteIp = (String) remoteMediaInfo.get("remoteIp");
            Integer remoteAudioPort = (Integer) remoteMediaInfo.get("audioPort");

            if (remoteIp == null || remoteAudioPort == null) {
                log.error("无效的媒体信息: {}", remoteMediaInfo);
                return;
            }

            log.info("对方媒体地址: {}:{}", remoteIp, remoteAudioPort);

            // 2. 启动音频发送
            audioRtpHandler.startSending(remoteIp, remoteAudioPort);

            // 3. 启动音频接收
            audioRtpHandler.startReceiving();

            mediaActive = true;
            log.info("音频通话已启动");

        } catch (Exception e) {
            log.error("启动音频通话失败", e);
        }
    }

    /**
     * 启动视频通话
     *
     * @param remoteMediaInfo 对方的媒体信息
     * @param videoRenderer 视频渲染器
     */
    public void startVideoCall(Map<String, Object> remoteMediaInfo, VideoRenderer videoRenderer) {
        try {
            log.info("启动视频通话");

            // 1. 启动音频通话
            startAudioCall(remoteMediaInfo);

            // 2. 获取对方的视频端口
            Boolean hasVideo = (Boolean) remoteMediaInfo.get("hasVideo");
            if (hasVideo == null || !hasVideo) {
                log.warn("对方不支持视频");
                return;
            }

            String remoteIp = (String) remoteMediaInfo.get("remoteIp");
            Integer remoteVideoPort = (Integer) remoteMediaInfo.get("videoPort");

            if (remoteIp == null || remoteVideoPort == null) {
                log.error("无效的视频媒体信息");
                return;
            }

            log.info("对方视频地址: {}:{}", remoteIp, remoteVideoPort);

            // 3. 启动视频发送
            videoRtpHandler.startSending(remoteIp, remoteVideoPort);

            // 4. 启动视频接收
            videoRtpHandler.startReceiving(videoRenderer);

            log.info("视频通话已启动");

        } catch (Exception e) {
            log.error("启动视频通话失败", e);
        }
    }

    /**
     * 停止媒体传输
     * ✅ 修复：完全关闭并重新初始化RTP处理器，避免状态不一致
     */
    public void stopMedia() {
        if (!mediaActive) {
            return;
        }

        log.info("停止媒体传输");

        // 1. 停止音频
        audioRtpHandler.stopSending();
        audioRtpHandler.stopReceiving();

        // 2. 停止视频
        videoRtpHandler.stopSending();
        videoRtpHandler.stopReceiving();

        // ✅ 关键修复：完全关闭RTP处理器，释放所有资源
        try {
            log.info("关闭并重新初始化RTP处理器（避免状态不一致）");

            // 关闭旧的handler
            audioRtpHandler.shutdown();
            videoRtpHandler.shutdown();

            // 重新创建handler（使用新的端口）
            audioRtpHandler = new AudioRtpHandler();
            audioRtpHandler.initialize(localIp, getNextAudioPort());

            videoRtpHandler = new VideoRtpHandler();
            videoRtpHandler.initialize(localIp, getNextVideoPort());

            log.info("✅ RTP处理器已重新初始化，可以进行下一次通话");
        } catch (Exception e) {
            log.error("重新初始化RTP处理器失败", e);
        }

        mediaActive = false;
        log.info("媒体传输已停止");
    }

    /**
     * 关闭媒体管理器
     */
    public void shutdown() {
        log.info("关闭 MediaManager");

        stopMedia();

        if (audioRtpHandler != null) {
            audioRtpHandler.shutdown();
        }

        if (videoRtpHandler != null) {
            videoRtpHandler.shutdown();
        }

        initialized = false;
        log.info("MediaManager 已关闭");
    }

    /**
     * 获取下一个可用的音频端口
     */
    private int getNextAudioPort() {
        return currentAudioPort++;
    }

    /**
     * 获取下一个可用的视频端口
     */
    private int getNextVideoPort() {
        return currentVideoPort++;
    }

    /**
     * 获取当前音频 RTP 端口
     */
    public int getCurrentAudioPort() {
        return currentAudioPort - 1;
    }

    /**
     * 获取当前视频 RTP 端口
     */
    public int getCurrentVideoPort() {
        return currentVideoPort - 1;
    }

    /**
     * 获取本地 IP
     */
    public String getLocalIp() {
        return localIp;
    }

    /**
     * 获取 RTCP 统计信息
     */
    public String getRtcpStats() {
        if (!mediaActive) {
            return "媒体未激活";
        }

        AudioRtpHandler.RtcpStats audioStats = audioRtpHandler.getRtcpStats();
        VideoRtpHandler.VideoRtpStats videoStats = videoRtpHandler.getStats();

        return String.format(
            "音频: %s\n视频: %s",
            audioStats, videoStats
        );
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 检查媒体是否激活
     */
    public boolean isMediaActive() {
        return mediaActive;
    }

    /**
     * 发送视频帧 (用于摄像头)
     *
     * @param frame JavaCV Frame 对象
     */
    public void sendVideoFrame(org.bytedeco.javacv.Frame frame) {
        if (videoRtpHandler != null) {
            // VideoRtpHandler 已经在 startSending 中启动了采集
            // 这里暂不实现，因为 VideoRtpHandler 自己管理采集
            log.warn("sendVideoFrame() 暂不支持外部 Frame 输入");
        }
    }

    /**
     * 发送屏幕共享帧 (用于屏幕共享)
     *
     * @param image BufferedImage 对象
     */
    public void sendScreenFrame(java.awt.image.BufferedImage image) {
        if (videoRtpHandler != null && image != null) {
            try {
                // 将 BufferedImage 转换为 JavaCV Frame
                org.bytedeco.javacv.Java2DFrameConverter converter =
                    new org.bytedeco.javacv.Java2DFrameConverter();
                org.bytedeco.javacv.Frame frame = converter.convert(image);

                // 通过 VideoRtpHandler 发送
                videoRtpHandler.sendExternalFrame(frame);
                log.debug("发送屏幕共享帧: {}x{}", image.getWidth(), image.getHeight());

            } catch (Exception e) {
                log.error("发送屏幕共享帧失败", e);
            }
        }
    }

    /**
     * 获取视频 RTP 处理器 (用于高级操作)
     */
    public VideoRtpHandler getVideoRtpHandler() {
        return videoRtpHandler;
    }

    /**
     * 获取音频 RTP 处理器 (用于高级操作)
     */
    public AudioRtpHandler getAudioRtpHandler() {
        return audioRtpHandler;
    }
}
