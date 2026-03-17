package com.sip.client.conference_media;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 视频合成器
 *
 * 将多路视频流合成为一个画面
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
public class VideoComposer {

    private static final Logger logger = LoggerFactory.getLogger(VideoComposer.class);

    // 视频参数
    private static final int OUTPUT_WIDTH = 1280;
    private static final int OUTPUT_HEIGHT = 720;
    private static final int FRAME_RATE = 25;

    // 视频流管理
    private final Map<Long, Mat> videoFrames = new ConcurrentHashMap<>();

    // 本地摄像头
    private OpenCVFrameGrabber localCamera;
    private boolean isLocalCaptureRunning = false;

    // 合成输出
    private boolean isRunning = false;
    private ExecutorService composingExecutor;
    private VideoFrameCallback callback;

    /**
     * 视频帧回调接口
     */
    public interface VideoFrameCallback {
        void onFrameComposed(byte[] frameData);
    }

    /**
     * 启动合成器
     */
    public void start(VideoFrameCallback callback) {
        if (isRunning) {
            logger.warn("视频合成器已在运行");
            return;
        }

        logger.info("启动视频合成器");

        this.callback = callback;
        isRunning = true;

        // 启动合成线程
        composingExecutor = Executors.newSingleThreadExecutor();
        composingExecutor.submit(this::composingLoop);

        logger.info("视频合成器已启动");
    }

    /**
     * 停止合成器
     */
    public void stop() {
        logger.info("停止视频合成器");

        isRunning = false;

        if (composingExecutor != null) {
            composingExecutor.shutdown();
        }

        stopLocalCapture();

        videoFrames.clear();

        logger.info("视频合成器已停止");
    }

    /**
     * 添加视频流
     */
    public void addVideoStream(Long userId) {
        logger.info("添加视频流: userId={}", userId);

        // 初始化空帧
        Mat emptyFrame = new Mat(480, 640, CV_8UC3);
        videoFrames.put(userId, emptyFrame);
    }

    /**
     * 移除视频流
     */
    public void removeVideoStream(Long userId) {
        logger.info("移除视频流: userId={}", userId);

        Mat frame = videoFrames.remove(userId);
        if (frame != null) {
            frame.release();
        }
    }

    /**
     * 接收视频帧 (来自 RTP)
     */
    public void onVideoFrame(Long userId, Mat frame) {
        if (!isRunning || frame == null) {
            return;
        }

        videoFrames.put(userId, frame.clone());
    }

    /**
     * 启动本地摄像头采集
     */
    public void startLocalCapture() {
        if (isLocalCaptureRunning) {
            return;
        }

        logger.info("启动本地摄像头采集");

        try {
            localCamera = new OpenCVFrameGrabber(0);
            localCamera.setImageWidth(640);
            localCamera.setImageHeight(480);
            localCamera.setFrameRate(FRAME_RATE);
            localCamera.start();

            isLocalCaptureRunning = true;

            // 启动采集线程
            new Thread(this::captureLoop).start();

            logger.info("本地摄像头采集已启动");
        } catch (FrameGrabber.Exception e) {
            logger.error("启动摄像头失败", e);
        }
    }

    /**
     * 停止本地摄像头采集
     */
    public void stopLocalCapture() {
        if (!isLocalCaptureRunning) {
            return;
        }

        logger.info("停止本地摄像头采集");

        isLocalCaptureRunning = false;

        if (localCamera != null) {
            try {
                localCamera.stop();
                localCamera.release();
            } catch (FrameGrabber.Exception e) {
                logger.error("停止摄像头失败", e);
            }
        }

        logger.info("本地摄像头采集已停止");
    }

    /**
     * 摄像头采集循环
     */
    private void captureLoop() {
        logger.info("摄像头采集线程已启动");

        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

        while (isLocalCaptureRunning) {
            try {
                Frame frame = localCamera.grab();
                if (frame != null) {
                    Mat mat = converter.convert(frame);
                    if (mat != null) {
                        // 更新本地视频帧 (userId = 0 表示本地)
                        videoFrames.put(0L, mat.clone());
                    }
                }

                Thread.sleep(1000 / FRAME_RATE);
            } catch (Exception e) {
                logger.error("采集视频帧失败", e);
            }
        }

        logger.info("摄像头采集线程已结束");
    }

    /**
     * 合成循环
     */
    private void composingLoop() {
        logger.info("视频合成线程已启动");

        while (isRunning) {
            try {
                // 合成视频画面
                Mat composedFrame = composeVideoFrames();

                if (composedFrame != null && callback != null) {
                    // 转换为 byte[] 并回调
                    byte[] frameData = matToByteArray(composedFrame);
                    callback.onFrameComposed(frameData);

                    composedFrame.release();
                }

                // 控制帧率
                Thread.sleep(1000 / FRAME_RATE);
            } catch (InterruptedException e) {
                logger.warn("合成线程被中断");
                break;
            } catch (Exception e) {
                logger.error("合成视频失败", e);
            }
        }

        logger.info("视频合成线程已结束");
    }

    /**
     * 合成视频画面
     *
     * 布局策略:
     * - 1 人: 全屏
     * - 2 人: 左右分屏 (1x2)
     * - 3-4 人: 2x2 网格
     * - 5-9 人: 3x3 网格
     */
    private Mat composeVideoFrames() {
        int participantCount = videoFrames.size();

        if (participantCount == 0) {
            // 返回黑屏
            return new Mat(OUTPUT_HEIGHT, OUTPUT_WIDTH, CV_8UC3, new Scalar(0, 0, 0, 0));
        }

        Mat composedFrame = new Mat(OUTPUT_HEIGHT, OUTPUT_WIDTH, CV_8UC3);

        if (participantCount == 1) {
            // 全屏显示
            Mat frame = videoFrames.values().iterator().next();
            resize(frame, composedFrame, new Size(OUTPUT_WIDTH, OUTPUT_HEIGHT));
        } else if (participantCount <= 4) {
            // 2x2 网格布局
            composeGrid(composedFrame, 2, 2);
        } else {
            // 3x3 网格布局
            composeGrid(composedFrame, 3, 3);
        }

        return composedFrame;
    }

    /**
     * 网格布局合成
     */
    private void composeGrid(Mat output, int rows, int cols) {
        int cellWidth = OUTPUT_WIDTH / cols;
        int cellHeight = OUTPUT_HEIGHT / rows;

        int index = 0;
        for (Map.Entry<Long, Mat> entry : videoFrames.entrySet()) {
            if (index >= rows * cols) {
                break;
            }

            Mat frame = entry.getValue();
            if (frame == null || frame.empty()) {
                index++;
                continue;
            }

            // 计算位置
            int row = index / cols;
            int col = index % cols;
            int x = col * cellWidth;
            int y = row * cellHeight;

            // 缩放视频帧
            Mat resizedFrame = new Mat();
            resize(frame, resizedFrame, new Size(cellWidth, cellHeight));

            // 复制到输出画面
            Rect roi = new Rect(x, y, cellWidth, cellHeight);
            resizedFrame.copyTo(output.apply(roi));

            resizedFrame.release();
            index++;
        }
    }

    /**
     * Mat 转 byte[]
     */
    private byte[] matToByteArray(Mat mat) {
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.data().get(buffer);
        return buffer;
    }
}
