package com.sip.client.media.audio;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 音频播放器
 * 使用 Java Sound API 播放音频到扬声器
 *
 * 功能:
 * 1. 打开扬声器设备
 * 2. 播放音频数据
 * 3. 使用队列缓冲音频数据
 * 4. Jitter Buffer (抖动缓冲)
 *
 * @author 成员2
 */
@Slf4j
public class AudioPlayer {

    // ========== 音频参数 (与 AudioCapture 相同) ==========
    private static final float SAMPLE_RATE = 8000.0f;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    // ========== 帧大小 ==========
    private static final int FRAME_SIZE = 160;  // 20ms @ 8kHz
    // ✅ 优化：增大硬件缓冲区以提升播放流畅度（从3200增至6400 = 400ms缓冲）
    private static final int BUFFER_SIZE = FRAME_SIZE * 2 * 20;  // 400ms 缓冲

    // ========== 音频组件 ==========
    private AudioFormat audioFormat;
    private SourceDataLine sourceDataLine;
    private boolean isPlaying = false;

    // ========== Jitter Buffer ==========
    private BlockingQueue<byte[]> jitterBuffer;
    // ✅ 优化：增大抖动缓冲区以抵抗网络抖动（从10增至20帧 = 400ms）
    private static final int JITTER_BUFFER_SIZE = 20;  // 最多缓冲 20 帧 (400ms)

    // ========== 播放线程 ==========
    private Thread playbackThread;

    /**
     * 初始化音频播放器
     */
    public void initialize() throws LineUnavailableException {
        log.info("初始化音频播放器");

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

        // 2. 获取扬声器设备
        DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

        if (!AudioSystem.isLineSupported(dataLineInfo)) {
            throw new LineUnavailableException("不支持的音频格式");
        }

        sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
        sourceDataLine.open(audioFormat, BUFFER_SIZE);

        // 3. 初始化 Jitter Buffer
        jitterBuffer = new LinkedBlockingQueue<>(JITTER_BUFFER_SIZE);

        log.info("音频播放器初始化成功");
    }

    /**
     * 开始播放
     */
    public void startPlayback() {
        if (isPlaying) {
            log.warn("音频播放已在进行中");
            return;
        }

        this.isPlaying = true;

        log.info("开始音频播放");

        // 启动播放
        sourceDataLine.start();

        // 创建播放线程
        playbackThread = new Thread(this::playbackLoop, "AudioPlaybackThread");
        playbackThread.start();
    }

    /**
     * 停止播放
     */
    public void stopPlayback() {
        if (!isPlaying) {
            return;
        }

        log.info("停止音频播放");

        isPlaying = false;

        if (sourceDataLine != null) {
            sourceDataLine.drain();  // 等待缓冲区播放完成
            sourceDataLine.stop();
            sourceDataLine.close();
        }

        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                log.warn("等待播放线程结束时被中断", e);
            }
        }

        // 清空 Jitter Buffer
        jitterBuffer.clear();

        log.info("音频播放已停止");
    }

    /**
     * 播放音频数据
     * 将音频数据放入 Jitter Buffer
     *
     * @param audioData PCM 音频数据
     */
    public void playAudio(byte[] audioData) {
        if (!isPlaying) {
            return;
        }

        try {
            // 如果 Jitter Buffer 已满,丢弃最旧的帧
            if (!jitterBuffer.offer(audioData)) {
                log.debug("Jitter Buffer 已满,丢弃一帧");
                jitterBuffer.poll();  // 移除最旧的
                jitterBuffer.offer(audioData);  // 添加新的
            }
        } catch (Exception e) {
            log.error("添加音频数据到 Jitter Buffer 失败", e);
        }
    }

    /**
     * 播放循环
     */
    private void playbackLoop() {
        log.debug("音频播放循环启动");

        while (isPlaying) {
            try {
                // 从 Jitter Buffer 获取音频数据
                byte[] audioData = jitterBuffer.poll();

                if (audioData != null) {
                    // 写入扬声器
                    sourceDataLine.write(audioData, 0, audioData.length);
                } else {
                    // 如果没有数据,等待一下
                    Thread.sleep(10);
                }

            } catch (InterruptedException e) {
                if (isPlaying) {
                    log.warn("音频播放线程被中断");
                }
                break;
            } catch (Exception e) {
                if (isPlaying) {
                    log.error("音频播放错误", e);
                }
                break;
            }
        }

        log.debug("音频播放循环结束");
    }

    /**
     * 获取音频格式
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * 检查是否正在播放
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 获取 Jitter Buffer 当前大小
     */
    public int getJitterBufferSize() {
        return jitterBuffer.size();
    }

    /**
     * 列出可用的扬声器设备
     */
    public static void listSpeakers() {
        log.info("可用的扬声器设备:");

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);

            Line.Info[] sourceLineInfos = mixer.getSourceLineInfo();
            if (sourceLineInfos.length > 0) {
                log.info("  - {}", mixerInfo.getName());
            }
        }
    }
}
