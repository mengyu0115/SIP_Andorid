package com.sip.client.media.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;

/**
 * 音频采集助手
 *
 * 功能：
 * - 从麦克风捕获音频数据
 * - 音频格式：16kHz, 16bit, 单声道
 * - 1024字节缓冲区
 * - 回调方式实时传输音频数据
 *
 * @author SIP Team
 * @version 1.0
 */
public class AudioCaptureHelper {

    private static final Logger logger = LoggerFactory.getLogger(AudioCaptureHelper.class);

    // ========== 音频格式配置 ==========
    private static final float SAMPLE_RATE = 16000.0f;  // 16kHz采样率
    private static final int SAMPLE_SIZE_IN_BITS = 16;   // 16位采样深度
    private static final int CHANNELS = 1;               // 单声道
    private static final boolean SIGNED = true;          // 有符号
    private static final boolean BIG_ENDIAN = false;     // 小端序

    private static final int BUFFER_SIZE = 1024;         // 缓冲区大小（字节）

    // ========== 音频设备 ==========
    private TargetDataLine targetLine;
    private AudioFormat audioFormat;
    private Thread captureThread;
    private volatile boolean isCapturing = false;

    // ========== 回调接口 ==========
    private AudioDataCallback callback;

    /**
     * 音频数据回调接口
     */
    public interface AudioDataCallback {
        /**
         * 接收音频数据
         *
         * @param audioData 音频数据字节数组
         * @param length 有效数据长度
         */
        void onAudioData(byte[] audioData, int length);
    }

    /**
     * 构造函数
     */
    public AudioCaptureHelper() {
        // 初始化音频格式
        audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        logger.info("AudioCaptureHelper 初始化完成");
        logger.info("音频格式: {}Hz, {}bit, {}声道",
            (int) SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS == 1 ? "单" : "立体");
    }

    /**
     * 启动音频采集
     *
     * @param callback 音频数据回调
     * @throws LineUnavailableException 如果音频线路不可用
     */
    public void startCapture(AudioDataCallback callback) throws LineUnavailableException {
        if (isCapturing) {
            logger.warn("音频采集已经在运行");
            return;
        }

        if (callback == null) {
            throw new IllegalArgumentException("回调不能为空");
        }

        this.callback = callback;

        try {
            // 获取音频输入线路信息
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            // 检查系统是否支持该音频格式
            if (!AudioSystem.isLineSupported(info)) {
                logger.error("系统不支持指定的音频格式");
                throw new LineUnavailableException("系统不支持指定的音频格式");
            }

            // 获取并打开音频线路
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(audioFormat, BUFFER_SIZE * 2); // 缓冲区设为2倍大小
            targetLine.start();

            logger.info("音频线路已打开并启动");

            // 启动采集线程
            isCapturing = true;
            captureThread = new Thread(this::captureAudio, "AudioCapture");
            captureThread.setDaemon(true);
            captureThread.start();

            logger.info("音频采集已启动");

        } catch (LineUnavailableException e) {
            logger.error("打开音频线路失败", e);
            cleanup();
            throw e;
        }
    }

    /**
     * 音频采集线程
     */
    private void captureAudio() {
        byte[] buffer = new byte[BUFFER_SIZE];

        logger.info("音频采集线程开始运行");

        try {
            while (isCapturing && targetLine != null) {
                // 从麦克风读取音频数据
                int bytesRead = targetLine.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    // 通过回调传递音频数据
                    if (callback != null) {
                        try {
                            callback.onAudioData(buffer, bytesRead);
                        } catch (Exception e) {
                            logger.error("回调处理音频数据时发生错误", e);
                        }
                    }
                } else if (bytesRead < 0) {
                    logger.warn("音频读取返回负值: {}", bytesRead);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("音频采集过程中发生错误", e);
        } finally {
            logger.info("音频采集线程结束");
        }
    }

    /**
     * 停止音频采集
     */
    public void stopCapture() {
        logger.info("停止音频采集");

        isCapturing = false;

        // 等待采集线程结束
        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(1000); // 等待最多1秒
            } catch (InterruptedException e) {
                logger.warn("等待采集线程结束时被中断", e);
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        cleanup();

        logger.info("音频采集已停止");
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        // 停止并关闭音频线路
        if (targetLine != null) {
            try {
                targetLine.stop();
                targetLine.close();
                logger.info("音频线路已关闭");
            } catch (Exception e) {
                logger.error("关闭音频线路时发生错误", e);
            }
            targetLine = null;
        }

        callback = null;
    }

    /**
     * 检查是否正在采集
     *
     * @return true如果正在采集，否则false
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * 获取音频格式
     *
     * @return 音频格式
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * 获取可用的音频输入设备列表
     *
     * @return 音频输入设备信息数组
     */
    public static Mixer.Info[] getAvailableAudioInputs() {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        java.util.List<Mixer.Info> inputs = new java.util.ArrayList<>();

        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLineInfos = mixer.getTargetLineInfo();

            if (targetLineInfos.length > 0) {
                inputs.add(mixerInfo);
            }
        }

        logger.info("找到 {} 个音频输入设备", inputs.size());
        return inputs.toArray(new Mixer.Info[0]);
    }

    /**
     * 测试音频采集（用于调试）
     *
     * @param durationSeconds 测试持续时间（秒）
     */
    public static void testCapture(int durationSeconds) {
        AudioCaptureHelper helper = new AudioCaptureHelper();

        try {
            logger.info("开始音频采集测试，持续 {} 秒", durationSeconds);

            // 统计接收到的音频数据
            final long[] totalBytes = {0};
            final long startTime = System.currentTimeMillis();

            helper.startCapture((audioData, length) -> {
                totalBytes[0] += length;

                // 每秒输出一次统计
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > 0 && elapsed % 1000 < 50) {
                    double kbps = (totalBytes[0] * 8.0 / 1024.0) / (elapsed / 1000.0);
                    logger.info("已采集: {} bytes, 速率: {:.2f} Kbps", totalBytes[0], kbps);
                }
            });

            // 等待指定时间
            Thread.sleep(durationSeconds * 1000L);

            helper.stopCapture();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("测试完成，总共采集: {} bytes, 耗时: {} ms", totalBytes[0], elapsed);

        } catch (Exception e) {
            logger.error("音频采集测试失败", e);
        }
    }

    /**
     * 主函数（用于独立测试）
     */
    public static void main(String[] args) {
        // 列出可用的音频输入设备
        logger.info("========== 可用的音频输入设备 ==========");
        Mixer.Info[] inputs = getAvailableAudioInputs();
        for (int i = 0; i < inputs.length; i++) {
            logger.info("[{}] {}", i, inputs[i].getName());
        }
        logger.info("=========================================");

        // 测试音频采集5秒
        testCapture(5);
    }
}
