package com.sip.client.conference_media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音频混音器
 *
 * 将多路音频流混合成一路输出
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
public class AudioMixer {

    private static final Logger logger = LoggerFactory.getLogger(AudioMixer.class);

    // 音频参数
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 1;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int FRAME_SIZE = 960; // 20ms @ 48kHz

    // 音频流管理
    private final Map<Long, AudioInputStream> audioStreams = new ConcurrentHashMap<>();
    private final Map<Long, byte[]> audioBuffers = new ConcurrentHashMap<>();

    // 输出
    private SourceDataLine outputLine;
    private boolean isRunning = false;
    private boolean isLocalMuted = false;

    private ExecutorService mixingExecutor;

    /**
     * 启动混音器
     */
    public void start() {
        if (isRunning) {
            logger.warn("混音器已在运行");
            return;
        }

        logger.info("启动音频混音器");

        try {
            // 打开音频输出设备
            AudioFormat format = new AudioFormat(
                SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, true, false
            );

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            outputLine = (SourceDataLine) AudioSystem.getLine(info);
            outputLine.open(format);
            outputLine.start();

            // 启动混音线程
            isRunning = true;
            mixingExecutor = Executors.newSingleThreadExecutor();
            mixingExecutor.submit(this::mixingLoop);

            logger.info("音频混音器已启动");
        } catch (LineUnavailableException e) {
            logger.error("打开音频输出设备失败", e);
        }
    }

    /**
     * 停止混音器
     */
    public void stop() {
        logger.info("停止音频混音器");

        isRunning = false;

        if (mixingExecutor != null) {
            mixingExecutor.shutdown();
        }

        if (outputLine != null) {
            outputLine.drain();
            outputLine.stop();
            outputLine.close();
        }

        audioStreams.clear();
        audioBuffers.clear();

        logger.info("音频混音器已停止");
    }

    /**
     * 添加音频流
     */
    public void addAudioStream(Long userId) {
        logger.info("添加音频流: userId={}", userId);

        // 初始化缓冲区
        audioBuffers.put(userId, new byte[FRAME_SIZE * 2]); // 16-bit samples
    }

    /**
     * 移除音频流
     */
    public void removeAudioStream(Long userId) {
        logger.info("移除音频流: userId={}", userId);

        audioStreams.remove(userId);
        audioBuffers.remove(userId);
    }

    /**
     * 接收音频数据 (来自 RTP)
     */
    public void onAudioData(Long userId, byte[] audioData) {
        if (!isRunning || audioData == null) {
            return;
        }

        // 更新缓冲区
        audioBuffers.put(userId, audioData);
    }

    /**
     * 静音本地音频
     */
    public void muteLocal() {
        isLocalMuted = true;
        logger.info("本地音频已静音");
    }

    /**
     * 取消静音本地音频
     */
    public void unmuteLocal() {
        isLocalMuted = false;
        logger.info("本地音频已取消静音");
    }

    /**
     * 混音循环
     */
    private void mixingLoop() {
        logger.info("混音线程已启动");

        byte[] outputBuffer = new byte[FRAME_SIZE * 2];

        while (isRunning) {
            try {
                // 混合所有音频流
                mixAudioStreams(outputBuffer);

                // 输出混音后的音频
                if (outputLine != null && outputLine.isOpen()) {
                    outputLine.write(outputBuffer, 0, outputBuffer.length);
                }

                // 控制帧率 (20ms 一帧)
                Thread.sleep(20);
            } catch (InterruptedException e) {
                logger.warn("混音线程被中断");
                break;
            } catch (Exception e) {
                logger.error("混音失败", e);
            }
        }

        logger.info("混音线程已结束");
    }

    /**
     * 混合音频流
     *
     * 算法: 加权求和 + 归一化
     */
    private void mixAudioStreams(byte[] outputBuffer) {
        if (audioBuffers.isEmpty()) {
            // 没有音频流，输出静音
            java.util.Arrays.fill(outputBuffer, (byte) 0);
            return;
        }

        short[] mixed = new short[outputBuffer.length / 2];
        int streamCount = 0;

        // 1. 将所有音频流相加
        for (Map.Entry<Long, byte[]> entry : audioBuffers.entrySet()) {
            byte[] audioData = entry.getValue();
            if (audioData == null || audioData.length < outputBuffer.length) {
                continue;
            }

            for (int i = 0; i < mixed.length; i++) {
                // 从 byte[] 读取 16-bit sample (little-endian)
                short sample = (short) ((audioData[i * 2 + 1] << 8) | (audioData[i * 2] & 0xFF));
                mixed[i] += sample;
            }

            streamCount++;
        }

        if (streamCount == 0) {
            java.util.Arrays.fill(outputBuffer, (byte) 0);
            return;
        }

        // 2. 归一化 (防止溢出)
        double normalizationFactor = 1.0 / Math.sqrt(streamCount);

        for (int i = 0; i < mixed.length; i++) {
            mixed[i] = (short) (mixed[i] * normalizationFactor);
        }

        // 3. 转换回 byte[]
        for (int i = 0; i < mixed.length; i++) {
            outputBuffer[i * 2] = (byte) (mixed[i] & 0xFF);
            outputBuffer[i * 2 + 1] = (byte) ((mixed[i] >> 8) & 0xFF);
        }
    }
}
