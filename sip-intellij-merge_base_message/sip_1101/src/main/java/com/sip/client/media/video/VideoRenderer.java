package com.sip.client.media.video;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;

import java.nio.ByteBuffer;

/**
 * 视频渲染器
 * 将视频帧渲染到 JavaFX Canvas
 *
 * 功能:
 * 1. 接收 JavaCV Frame
 * 2. 转换为 JavaFX Image
 * 3. 渲染到 Canvas
 *
 * @author 成员2
 */
@Slf4j
public class VideoRenderer {

    // ========== JavaFX Canvas ==========
    private Canvas canvas;
    private GraphicsContext graphicsContext;

    // ========== 视频参数 ==========
    private int videoWidth;
    private int videoHeight;

    // ========== 缓存 ==========
    private WritableImage writableImage;

    /**
     * 初始化视频渲染器
     *
     * @param canvas JavaFX Canvas
     * @param videoWidth 视频宽度
     * @param videoHeight 视频高度
     */
    public void initialize(Canvas canvas, int videoWidth, int videoHeight) {
        this.canvas = canvas;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;

        this.graphicsContext = canvas.getGraphicsContext2D();

        // 创建 WritableImage 缓存
        this.writableImage = new WritableImage(videoWidth, videoHeight);

        log.info("视频渲染器初始化成功: {}x{}", videoWidth, videoHeight);
    }

    /**
     * 渲染视频帧
     *
     * @param frame JavaCV Frame
     */
    public void renderFrame(Frame frame) {
        if (frame == null || frame.image == null) {
            return;
        }

        try {
            // 1. 从 Frame 提取 BGR 数据
            ByteBuffer buffer = (ByteBuffer) frame.image[0];
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            // 2. 转换 BGR -> RGB
            byte[] rgbData = convertBGRtoRGB(data, videoWidth, videoHeight);

            // 3. 更新 WritableImage
            PixelWriter pixelWriter = writableImage.getPixelWriter();
            pixelWriter.setPixels(
                0, 0,
                videoWidth, videoHeight,
                PixelFormat.getByteRgbInstance(),
                rgbData, 0,
                videoWidth * 3
            );

            // 4. 在 JavaFX 线程中绘制
            Platform.runLater(() -> {
                graphicsContext.drawImage(writableImage, 0, 0, canvas.getWidth(), canvas.getHeight());
            });

        } catch (Exception e) {
            log.error("渲染视频帧失败", e);
        }
    }

    /**
     * 渲染 RGB 字节数组
     * (用于解码后的视频数据)
     *
     * @param rgbData RGB 字节数组
     * @param width 宽度
     * @param height 高度
     */
    public void renderRGB(byte[] rgbData, int width, int height) {
        try {
            // 如果尺寸变化,重新创建 WritableImage
            if (width != videoWidth || height != videoHeight) {
                videoWidth = width;
                videoHeight = height;
                writableImage = new WritableImage(width, height);
            }

            // 更新 WritableImage
            PixelWriter pixelWriter = writableImage.getPixelWriter();
            pixelWriter.setPixels(
                0, 0,
                width, height,
                PixelFormat.getByteRgbInstance(),
                rgbData, 0,
                width * 3
            );

            // 在 JavaFX 线程中绘制
            Platform.runLater(() -> {
                graphicsContext.drawImage(writableImage, 0, 0, canvas.getWidth(), canvas.getHeight());
            });

        } catch (Exception e) {
            log.error("渲染 RGB 数据失败", e);
        }
    }

    /**
     * 清空画布
     */
    public void clear() {
        Platform.runLater(() -> {
            graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    /**
     * 转换 BGR 到 RGB
     * OpenCV 默认使用 BGR 格式,JavaFX 需要 RGB
     */
    private byte[] convertBGRtoRGB(byte[] bgrData, int width, int height) {
        byte[] rgbData = new byte[bgrData.length];

        for (int i = 0; i < bgrData.length; i += 3) {
            rgbData[i] = bgrData[i + 2];      // R
            rgbData[i + 1] = bgrData[i + 1];  // G
            rgbData[i + 2] = bgrData[i];      // B
        }

        return rgbData;
    }
}
