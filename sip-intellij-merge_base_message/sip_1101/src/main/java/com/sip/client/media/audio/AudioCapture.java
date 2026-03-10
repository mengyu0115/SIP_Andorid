package com.sip.client.media.audio;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

/**
 * 音频采集器
 * 使用 Java Sound API 从麦克风采集音频
 *
 * 功能:
 * 1. 打开麦克风设备
 * 2. 采集音频数据
 * 3. 提供音频流回调
 *
 * 音频格式:
 * - 采样率: 8000 Hz
 * - 采样位数: 16 bit
 * - 声道: 单声道 (Mono)
 * - 编码: PCM signed
 *
 * @author 成员2
 */
@Slf4j
public class AudioCapture {

    // ========== 音频参数 ==========
    private static final float SAMPLE_RATE = 8000.0f;  // 8 KHz (适合语音)
    private static final int SAMPLE_SIZE_IN_BITS = 16;  // 16 bit
    private static final int CHANNELS = 1;              // 单声道
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    // ========== 帧大小 (每次读取) ==========
    private static final int FRAME_SIZE = 160;  // 160 samples = 20ms @ 8kHz
    // ✅ 优化：增大缓冲区以提升音质，避免断断续续（从320增至1280 = 80ms缓冲）
    private static final int BUFFER_SIZE = FRAME_SIZE * 2 * 4;  // 640 samples * 2 bytes = 1280 bytes (80ms)

    // ========== 音频组件 ==========
    private AudioFormat audioFormat;
    private TargetDataLine targetDataLine;
    private boolean isCapturing = false;

    // ========== 回调接口 ==========
    private AudioDataCallback callback;

    // ========== 采集线程 ==========
    private Thread captureThread;

    /**
     * 初始化音频采集器
     */
    public void initialize() throws LineUnavailableException {
        log.info("初始化音频采集器");

        // 1. 创建音频格式
        audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        log.info("音频格式: {}Hz, {}bit, {} 声道",
            (int) SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS);

        // 2. 获取麦克风设备
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException("不支持的音频格式");
        }

        targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        targetDataLine.open(audioFormat, BUFFER_SIZE);

        log.info("音频采集器初始化成功");
    }

    /**
     * 开始采集
     */
    public void startCapture(AudioDataCallback callback) {
        if (isCapturing) {
            log.warn("音频采集已在进行中");
            return;
        }

        this.callback = callback;
        this.isCapturing = true;

        log.info("开始音频采集");

        // 启动采集
        targetDataLine.start();

        // 创建采集线程
        captureThread = new Thread(this::captureLoop, "AudioCaptureThread");
        captureThread.start();
    }

    /**
     * 停止采集
     */
    public void stopCapture() {
        if (!isCapturing) {
            return;
        }

        log.info("停止音频采集");

        isCapturing = false;

        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
        }

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                log.warn("等待采集线程结束时被中断", e);
            }
        }

        log.info("音频采集已停止");
    }

    /**
     * 采集循环
     */
    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];

        log.debug("音频采集循环启动");

        while (isCapturing) {
            try {
                // 从麦克风读取音频数据
                int bytesRead = targetDataLine.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    // 回调通知
                    if (callback != null) {
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                        callback.onAudioData(audioData);
                    }
                }

            } catch (Exception e) {
                if (isCapturing) {
                    log.error("音频采集错误", e);
                }
                break;
            }
        }

        log.debug("音频采集循环结束");
    }

    /**
     * 获取音频格式
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * 检查是否正在采集
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * 音频数据回调接口
     */
    public interface AudioDataCallback {
        /**
         * 接收到音频数据
         *
         * @param audioData PCM 音频数据
         */
        void onAudioData(byte[] audioData);
    }

    /**
     * 列出可用的麦克风设备
     */
    public static void listMicrophones() {
        log.info("可用的麦克风设备:");

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);

            Line.Info[] targetLineInfos = mixer.getTargetLineInfo();
            if (targetLineInfos.length > 0) {
                log.info("  - {}", mixerInfo.getName());
            }
        }
    }
}
