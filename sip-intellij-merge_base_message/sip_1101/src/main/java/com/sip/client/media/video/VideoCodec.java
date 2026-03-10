package com.sip.client.media.video;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 简化的视频编解码器
 * 使用 JPEG 编解码替代 H.264，简化实现并降低复杂度
 *
 * JPEG 参数:
 * - 质量: 0.7 (70%)
 * - 格式: JPEG
 * - 适合: 学习、演示、低要求场景
 *
 * 优点:
 * - 实现简单，不需要复杂的编解码库
 * - 使用 Java 标准库
 * - 每帧独立，不依赖前帧
 *
 * 缺点:
 * - 带宽占用较大
 * - 压缩率不如 H.264
 * - 不适合高帧率场景
 *
 * @author 成员2
 * @version 2.0 - 简化 JPEG 实现
 */
@Slf4j
public class VideoCodec {

    // ========== 视频参数 ==========
    private int videoWidth;
    private int videoHeight;
    private double frameRate;

    // ========== JPEG 质量参数 ==========
    private static final float JPEG_QUALITY = 0.7f;  // 70% 质量

    // ========== 转换器 ==========
    private final Java2DFrameConverter converter = new Java2DFrameConverter();

    /**
     * 初始化编码器
     */
    public void initializeEncoder(int videoWidth, int videoHeight, double frameRate) throws Exception {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.frameRate = frameRate;

        log.info("初始化 JPEG 编码器: {}x{} @ {}fps, 质量: {}%",
                 videoWidth, videoHeight, frameRate, (int)(JPEG_QUALITY * 100));
        log.info("JPEG 编码器初始化成功");
    }

    /**
     * 编码一帧 (Frame -> JPEG)
     *
     * @param frame JavaCV Frame
     * @return JPEG 编码后的字节数组
     */
    public byte[] encodeFrame(Frame frame) {
        if (frame == null) {
            log.warn("Frame 为 null，无法编码");
            return null;
        }

        if (frame.image == null) {
            log.warn("Frame.image 为 null，无法编码");
            return null;
        }

        try {
            // 1. 将 Frame 转换为 BufferedImage
            BufferedImage bufferedImage = converter.convert(frame);
            if (bufferedImage == null) {
                log.error("Frame 转换为 BufferedImage 失败：converter.convert() 返回 null");
                return null;
            }

            // 2. 将 BufferedImage 编码为 JPEG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // 使用 ImageIO 写入 JPEG
            boolean success = ImageIO.write(bufferedImage, "JPEG", outputStream);

            if (!success) {
                log.error("JPEG 编码失败：ImageIO.write() 返回 false，图像尺寸: {}x{}, 类型: {}",
                         bufferedImage.getWidth(), bufferedImage.getHeight(), bufferedImage.getType());
                return null;
            }

            byte[] jpegData = outputStream.toByteArray();

            if (jpegData.length == 0) {
                log.error("JPEG 编码失败：输出数据为空");
                return null;
            }

            log.trace("JPEG 编码成功: {} bytes", jpegData.length);
            return jpegData;

        } catch (Exception e) {
            log.error("编码视频帧失败：异常信息", e);
            return null;
        }
    }

    /**
     * 停止编码器
     */
    public void stopEncoder() {
        log.info("JPEG 编码器已停止");
        // JPEG 编码器无需特殊清理
    }

    /**
     * 初始化解码器
     */
    public void initializeDecoder() throws Exception {
        log.info("初始化 JPEG 解码器");
        log.info("JPEG 解码器已准备就绪");
        // JPEG 解码器无需特殊初始化
    }

    /**
     * 解码一帧 (JPEG -> Frame)
     *
     * @param encodedData JPEG 编码数据
     * @return JavaCV Frame
     */
    public Frame decodeFrame(byte[] encodedData) {
        if (encodedData == null || encodedData.length == 0) {
            return null;
        }

        try {
            // 1. 将 JPEG 数据解码为 BufferedImage
            ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedData);
            BufferedImage bufferedImage = ImageIO.read(inputStream);

            if (bufferedImage == null) {
                log.warn("JPEG 解码失败");
                return null;
            }

            // 2. 将 BufferedImage 转换为 Frame
            Frame frame = converter.convert(bufferedImage);

            log.trace("JPEG 解码成功: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
            return frame;

        } catch (Exception e) {
            log.error("解码视频帧失败", e);
            return null;
        }
    }

    /**
     * 停止解码器
     */
    public void stopDecoder() {
        log.info("JPEG 解码器已停止");
        // JPEG 解码器无需特殊清理
    }

    /**
     * 将 Frame 转换为 RGB 字节数组
     * (用于 JavaFX 渲染)
     */
    public static byte[] frameToRGB(Frame frame) {
        if (frame == null || frame.image == null) {
            return null;
        }

        try {
            // 使用 Java2DFrameConverter 转换
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage bufferedImage = converter.convert(frame);

            if (bufferedImage == null) {
                return null;
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            byte[] rgbData = new byte[width * height * 3];

            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = bufferedImage.getRGB(x, y);

                    rgbData[index++] = (byte) ((rgb >> 16) & 0xFF);  // R
                    rgbData[index++] = (byte) ((rgb >> 8) & 0xFF);   // G
                    rgbData[index++] = (byte) (rgb & 0xFF);          // B
                }
            }

            return rgbData;

        } catch (Exception e) {
            log.error("Frame 转 RGB 失败", e);
            return null;
        }
    }

    /**
     * 将 RGB 字节数组转换为 Frame
     * (用于编码前的预处理)
     */
    public static Frame rgbToFrame(byte[] rgbData, int width, int height) {
        try {
            // 创建 BufferedImage
            BufferedImage bufferedImage =
                new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = rgbData[index++] & 0xFF;
                    int g = rgbData[index++] & 0xFF;
                    int b = rgbData[index++] & 0xFF;

                    int rgb = (r << 16) | (g << 8) | b;
                    bufferedImage.setRGB(x, y, rgb);
                }
            }

            // 转换为 Frame
            Java2DFrameConverter converter = new Java2DFrameConverter();
            return converter.convert(bufferedImage);

        } catch (Exception e) {
            log.error("RGB 转 Frame 失败", e);
            return null;
        }
    }

    /**
     * 将 BufferedImage 直接编码为 JPEG
     * (用于屏幕共享等场景)
     */
    public static byte[] encodeBufferedImage(BufferedImage image) {
        if (image == null) {
            return null;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "JPEG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("BufferedImage 编码失败", e);
            return null;
        }
    }

    /**
     * 将 JPEG 数据解码为 BufferedImage
     * (用于快速渲染)
     */
    public static BufferedImage decodeToBufferedImage(byte[] jpegData) {
        if (jpegData == null || jpegData.length == 0) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(jpegData);
            return ImageIO.read(inputStream);
        } catch (Exception e) {
            log.error("JPEG 解码失败", e);
            return null;
        }
    }
}
