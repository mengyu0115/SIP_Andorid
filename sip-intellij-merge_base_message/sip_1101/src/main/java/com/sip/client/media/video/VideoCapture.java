package com.sip.client.media.video;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

/**
 * 视频采集器
 * 使用 JavaCV (OpenCV) 从摄像头采集视频
 *
 * 功能:
 * 1. 打开摄像头设备
 * 2. 采集视频帧 (Frame)
 * 3. 提供视频流回调
 *
 * 视频参数:
 * - 分辨率: 640x480 (VGA)
 * - 帧率: 10 FPS (适配JPEG编码带宽)
 * - 格式: BGR (OpenCV 默认)
 *
 * @author 成员2
 */
@Slf4j
public class VideoCapture {

    // ========== 视频参数 ==========
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final double FRAME_RATE = 10.0;  // 降低到10 FPS以适配JPEG编码

    // ========== 摄像头组件 ==========
    private FrameGrabber grabber;
    private boolean isCapturing = false;
    private boolean isPrewarmed = false;  // ✅ 标记是否已预热

    // ========== 回调接口 ==========
    private VideoFrameCallback callback;

    // ========== 采集线程 ==========
    private Thread captureThread;

    /**
     * 初始化视频采集器
     */
    public void initialize() throws FrameGrabber.Exception {
        initialize(0);  // 默认使用第一个摄像头
    }

    /**
     * 初始化视频采集器
     *
     * @param deviceId 摄像头设备 ID (0, 1, 2, ...)
     */
    public void initialize(int deviceId) throws FrameGrabber.Exception {
        if (isPrewarmed) {
            log.info("摄像头已预热，跳过初始化");
            return;
        }

        log.info("初始化视频采集器: 设备 {}", deviceId);

        // 使用OpenCV采集（稳定兼容）
        grabber = new OpenCVFrameGrabber(deviceId);

        // 设置视频参数
        grabber.setImageWidth(VIDEO_WIDTH);
        grabber.setImageHeight(VIDEO_HEIGHT);
        grabber.setFrameRate(FRAME_RATE);

        // 启动采集器
        grabber.start();

        log.info("视频采集器初始化成功: {}x{} @ {}fps",
            VIDEO_WIDTH, VIDEO_HEIGHT, FRAME_RATE);
        isPrewarmed = true;
    }

    /**
     * ✅ 预热摄像头（登录时后台调用，避免打视频通话时初始化慢）
     * 只初始化grabber，不启动采集，减少资源占用
     *
     * @param deviceId 摄像头设备 ID
     * @throws FrameGrabber.Exception 初始化异常
     */
    public void prewarm(int deviceId) throws FrameGrabber.Exception {
        if (isPrewarmed && grabber != null) {
            log.info("摄像头已预热，跳过");
            return;
        }

        log.info("🔥 预热摄像头: 设备 {}", deviceId);

        // 使用OpenCV采集（稳定兼容）
        grabber = new OpenCVFrameGrabber(deviceId);

        // 设置视频参数
        grabber.setImageWidth(VIDEO_WIDTH);
        grabber.setImageHeight(VIDEO_HEIGHT);
        grabber.setFrameRate(FRAME_RATE);

        // ⚡ 修复问题1：预热时启动grabber并抓取一帧，确保grabber处于正常工作状态
        // 这样避免首次通话时grabber状态不正确导致 "Could not retrieve frame" 错误
        grabber.start();

        try {
            // 抓取第一帧，激活grabber的内部状态机
            Frame testFrame = grabber.grab();
            if (testFrame != null) {
                log.info("✅ 摄像头预热成功: 测试帧 {}x{}", testFrame.imageWidth, testFrame.imageHeight);
            }
        } catch (Exception e) {
            log.warn("⚠️ 预热时抓取测试帧失败（可忽略）: {}", e.getMessage());
        }

        isPrewarmed = true;
        log.info("✅ 摄像头预热完成: {}x{} @ {}fps (grabber已启动，可直接使用)",
            VIDEO_WIDTH, VIDEO_HEIGHT, FRAME_RATE);
    }

    /**
     * 开始采集
     */
    public void startCapture(VideoFrameCallback callback) {
        if (isCapturing) {
            log.warn("视频采集已在进行中");
            return;
        }

        this.callback = callback;
        this.isCapturing = true;

        log.info("开始视频采集");

        // 创建采集线程
        captureThread = new Thread(this::captureLoop, "VideoCaptureThread");
        captureThread.start();
    }

    /**
     * 停止采集
     */
    public void stopCapture() {
        if (!isCapturing) {
            return;
        }

        log.info("停止视频采集");

        isCapturing = false;

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                log.warn("等待采集线程结束时被中断", e);
            }
        }

        // ⚡ 修复画面撕裂问题：清空grabber缓冲区，避免下次启动时读取旧帧
        if (grabber != null && isPrewarmed) {
            try {
                log.debug("清空grabber缓冲区...");
                // 抓取并丢弃3帧，清空缓冲区中的旧帧
                for (int i = 0; i < 3; i++) {
                    Frame oldFrame = grabber.grab();
                    if (oldFrame != null) {
                        // 立即释放帧资源
                        oldFrame.close();
                    }
                }
                log.debug("✅ grabber缓冲区已清空");
            } catch (Exception e) {
                log.warn("⚠️ 清空grabber缓冲区失败: {}", e.getMessage());
            }
        }

        // grabber仍然可用，isPrewarmed仍为true，下次startCapture可以直接使用
        log.info("视频采集已停止（grabber保持预热状态，可重用）");
    }

    /**
     * ✅ 完全释放摄像头资源（用于登出或应用退出时调用）
     */
    public void release() {
        log.info("完全释放摄像头资源");

        // 先停止采集
        stopCapture();

        // 释放grabber
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (Exception e) {
            log.error("释放 FrameGrabber 失败", e);
        }

        // 重置预热状态
        isPrewarmed = false;

        log.info("摄像头资源已完全释放");
    }

    /**
     * 采集循环
     */
    private void captureLoop() {
        log.debug("视频采集循环启动");

        long frameInterval = (long) (1000.0 / FRAME_RATE);  // 毫秒
        long lastFrameTime = System.currentTimeMillis();

        while (isCapturing) {
            try {
                // 控制帧率
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastFrameTime;

                if (elapsed < frameInterval) {
                    Thread.sleep(frameInterval - elapsed);
                }

                lastFrameTime = System.currentTimeMillis();

                // 抓取一帧
                Frame frame = grabber.grab();

                if (frame != null && frame.image != null) {
                    // 回调通知
                    if (callback != null) {
                        callback.onVideoFrame(frame);
                    }
                }

            } catch (InterruptedException e) {
                if (isCapturing) {
                    log.warn("视频采集线程被中断");
                }
                break;
            } catch (Exception e) {
                if (isCapturing) {
                    log.error("视频采集错误", e);
                }
                break;
            }
        }

        log.debug("视频采集循环结束");
    }

    /**
     * 检查是否正在采集
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * ⚡ CRITICAL FIX #9: 检查摄像头是否已完成预热
     * 用于避免在预热未完成时就使用grabber导致 "Could not retrieve frame" 错误
     *
     * @return true 如果预热已完成且grabber可用，false 如果预热未完成或失败
     */
    public boolean isPrewarmed() {
        return isPrewarmed && grabber != null;
    }

    /**
     * 获取视频宽度
     */
    public int getVideoWidth() {
        return VIDEO_WIDTH;
    }

    /**
     * 获取视频高度
     */
    public int getVideoHeight() {
        return VIDEO_HEIGHT;
    }

    /**
     * 获取帧率
     */
    public double getFrameRate() {
        return FRAME_RATE;
    }

    /**
     * 视频帧回调接口
     */
    public interface VideoFrameCallback {
        /**
         * 接收到视频帧
         *
         * @param frame JavaCV Frame 对象
         */
        void onVideoFrame(Frame frame);
    }

    /**
     * 列出可用的摄像头设备
     */
    public static void listCameras() {
        log.info("可用的摄像头设备:");

        for (int i = 0; i < 5; i++) {
            try {
                OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(i);
                grabber.start();
                log.info("  - 设备 {}: {}x{}", i,
                    grabber.getImageWidth(), grabber.getImageHeight());
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                // 设备不存在,跳过
                break;
            }
        }
    }
}
