package com.sip.client.media.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 音频播放助手
 *
 * 功能：
 * - 播放接收到的音频数据
 * - 音频格式：16kHz, 16bit, 单声道
 * - 缓冲队列避免丢帧
 * - 多线程安全播放
 *
 * @author SIP Team
 * @version 1.0
 */
public class AudioPlayerHelper {

    private static final Logger logger = LoggerFactory.getLogger(AudioPlayerHelper.class);

    // ========== 音频格式配置 ==========
    private static final float SAMPLE_RATE = 16000.0f;  // 16kHz采样率
    private static final int SAMPLE_SIZE_IN_BITS = 16;   // 16位采样深度
    private static final int CHANNELS = 1;               // 单声道
    private static final boolean SIGNED = true;          // 有符号
    private static final boolean BIG_ENDIAN = false;     // 小端序

    private static final int BUFFER_SIZE = 4096;         // 播放缓冲区大小（字节）

    // ========== 音频设备 ==========
    private SourceDataLine sourceLine;
    private AudioFormat audioFormat;
    private Thread playbackThread;
    private volatile boolean isPlaying = false;

    // ========== 音频数据队列 ==========
    private BlockingQueue<byte[]> audioQueue;
    private static final int QUEUE_CAPACITY = 50; // 最多缓存50个音频包

    /**
     * 构造函数
     */
    public AudioPlayerHelper() {
        // 初始化音频格式
        audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        // 初始化队列
        audioQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        logger.info("AudioPlayerHelper 初始化完成");
        logger.info("音频格式: {}Hz, {}bit, {}声道",
            (int) SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS == 1 ? "单" : "立体");
    }

    /**
     * 初始化音频播放
     *
     * @throws LineUnavailableException 如果音频线路不可用
     */
    public void initialize() throws LineUnavailableException {
        if (isPlaying) {
            logger.warn("音频播放已经在运行");
            return;
        }

        try {
            // 获取音频输出线路信息
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

            // 检查系统是否支持该音频格式
            if (!AudioSystem.isLineSupported(info)) {
                logger.error("系统不支持指定的音频格式");
                throw new LineUnavailableException("系统不支持指定的音频格式");
            }

            // 获取并打开音频线路
            sourceLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceLine.open(audioFormat, BUFFER_SIZE);
            sourceLine.start();

            logger.info("音频播放线路已打开并启动");

            // 启动播放线程
            isPlaying = true;
            playbackThread = new Thread(this::playAudio, "AudioPlayback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            logger.info("音频播放已启动");

        } catch (LineUnavailableException e) {
            logger.error("打开音频播放线路失败", e);
            cleanup();
            throw e;
        }
    }

    /**
     * 播放音频数据
     *
     * @param audioData 音频数据字节数组
     * @param length 有效数据长度
     */
    public void playAudioData(byte[] audioData, int length) {
        if (!isPlaying) {
            logger.warn("音频播放未初始化");
            return;
        }

        if (audioData == null || length <= 0) {
            logger.warn("无效的音频数据");
            return;
        }

        try {
            // 复制有效数据
            byte[] data = new byte[length];
            System.arraycopy(audioData, 0, data, 0, length);

            // 添加到队列，如果队列满了则丢弃最旧的数据
            if (!audioQueue.offer(data)) {
                audioQueue.poll(); // 移除最旧的数据
                audioQueue.offer(data); // 添加新数据
                logger.trace("音频队列已满，丢弃旧数据");
            }

        } catch (Exception e) {
            logger.error("添加音频数据到队列失败", e);
        }
    }

    /**
     * 音频播放线程
     */
    private void playAudio() {
        logger.info("音频播放线程开始运行");

        try {
            while (isPlaying) {
                // 从队列中取出音频数据
                byte[] audioData = audioQueue.poll();

                if (audioData != null) {
                    // 写入音频输出线路
                    if (sourceLine != null && sourceLine.isOpen()) {
                        sourceLine.write(audioData, 0, audioData.length);
                    }
                } else {
                    // 队列为空，短暂休眠
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        logger.warn("播放线程被中断", e);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("音频播放过程中发生错误", e);
        } finally {
            logger.info("音频播放线程结束");
        }
    }

    /**
     * 停止音频播放
     */
    public void stop() {
        logger.info("停止音频播放");

        isPlaying = false;

        // 等待播放线程结束
        if (playbackThread != null && playbackThread.isAlive()) {
            try {
                playbackThread.join(1000); // 等待最多1秒
            } catch (InterruptedException e) {
                logger.warn("等待播放线程结束时被中断", e);
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }

        cleanup();

        logger.info("音频播放已停止");
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        // 清空队列
        if (audioQueue != null) {
            audioQueue.clear();
        }

        // 停止并关闭音频线路
        if (sourceLine != null) {
            try {
                sourceLine.drain(); // 播放完缓冲区中的所有数据
                sourceLine.stop();
                sourceLine.close();
                logger.info("音频播放线路已关闭");
            } catch (Exception e) {
                logger.error("关闭音频播放线路时发生错误", e);
            }
            sourceLine = null;
        }
    }

    /**
     * 检查是否正在播放
     *
     * @return true如果正在播放，否则false
     */
    public boolean isPlaying() {
        return isPlaying;
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
     * 获取当前队列中的音频包数量
     *
     * @return 队列大小
     */
    public int getQueueSize() {
        return audioQueue != null ? audioQueue.size() : 0;
    }

    /**
     * 获取可用的音频输出设备列表
     *
     * @return 音频输出设备信息数组
     */
    public static Mixer.Info[] getAvailableAudioOutputs() {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        java.util.List<Mixer.Info> outputs = new java.util.ArrayList<>();

        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] sourceLineInfos = mixer.getSourceLineInfo();

            if (sourceLineInfos.length > 0) {
                outputs.add(mixerInfo);
            }
        }

        logger.info("找到 {} 个音频输出设备", outputs.size());
        return outputs.toArray(new Mixer.Info[0]);
    }

    /**
     * 测试音频播放（用于调试）
     *
     * @param durationSeconds 测试持续时间（秒）
     */
    public static void testPlayback(int durationSeconds) {
        AudioPlayerHelper helper = new AudioPlayerHelper();

        try {
            logger.info("开始音频播放测试，持续 {} 秒", durationSeconds);

            helper.initialize();

            // 生成测试音频（440Hz正弦波 - A音符）
            float frequency = 440.0f;
            int sampleRate = (int) SAMPLE_RATE;
            byte[] buffer = new byte[1024];

            long startTime = System.currentTimeMillis();
            long totalSamples = 0;

            while ((System.currentTimeMillis() - startTime) < durationSeconds * 1000L) {
                // 生成正弦波数据
                for (int i = 0; i < buffer.length / 2; i++) {
                    double angle = 2.0 * Math.PI * totalSamples / sampleRate * frequency;
                    short sample = (short) (Math.sin(angle) * 16384); // 50% volume

                    // 小端序
                    buffer[i * 2] = (byte) (sample & 0xFF);
                    buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);

                    totalSamples++;
                }

                helper.playAudioData(buffer, buffer.length);

                // 控制生成速率
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }

            helper.stop();

            logger.info("测试完成");

        } catch (Exception e) {
            logger.error("音频播放测试失败", e);
        }
    }

    /**
     * 主函数（用于独立测试）
     */
    public static void main(String[] args) {
        // 列出可用的音频输出设备
        logger.info("========== 可用的音频输出设备 ==========");
        Mixer.Info[] outputs = getAvailableAudioOutputs();
        for (int i = 0; i < outputs.length; i++) {
            logger.info("[{}] {}", i, outputs[i].getName());
        }
        logger.info("=========================================");

        // 测试音频播放3秒（440Hz A音符）
        testPlayback(3);
    }
}
