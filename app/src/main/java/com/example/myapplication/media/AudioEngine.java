package com.example.myapplication.media;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频引擎 — RTP 采集 + 播放
 *
 * 编解码：G.711 μ-law (PCMU)，8000Hz 采样，单声道，每帧 160 样本 = 20ms
 *
 * RTP 包结构（12字节头 + 160字节 payload）：
 *   V=2, P=0, X=0, CC=0, M=0, PT=0(PCMU)
 *   sequence number (16bit)
 *   timestamp (32bit, 每帧+160)
 *   SSRC (32bit, 随机)
 */
public class AudioEngine {

    private static final String TAG = "AudioEngine";

    // 音频参数
    private static final int SAMPLE_RATE = 8000;
    private static final int FRAME_SIZE = 160;      // 20ms @ 8kHz = 160 samples
    private static final int RTP_HEADER_SIZE = 12;
    private static final int RTP_PAYLOAD_TYPE = 0;   // PCMU

    // RTP
    private DatagramSocket rtpSocket;
    private int localRtpPort;
    private String remoteIp;
    private int remoteRtpPort;
    private int ssrc;

    // InetAddress 缓存，避免每包 DNS 解析
    private InetAddress remoteAddr;

    // 状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);

    // 线程
    private Thread captureThread;
    private Thread playbackThread;

    // Android 音频组件
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    // 统计
    private long packetsSent, packetsReceived;

    /**
     * 初始化音频引擎
     */
    public void init(int localPort, String remoteIp, int remotePort) {
        this.localRtpPort = localPort;
        this.remoteIp = remoteIp;
        this.remoteRtpPort = remotePort;
        this.ssrc = (int) (Math.random() * Integer.MAX_VALUE);
        try {
            this.remoteAddr = InetAddress.getByName(remoteIp);
        } catch (Exception e) {
            Log.e(TAG, "解析远端IP失败: " + remoteIp, e);
        }
        Log.i(TAG, "初始化: local=" + localPort + ", remote=" + remoteIp + ":" + remotePort);
    }

    /**
     * 启动音频双向传输
     */
    public void start() {
        if (running.getAndSet(true)) return;

        try {
            rtpSocket = new DatagramSocket(localRtpPort);
            rtpSocket.setSoTimeout(500);
            rtpSocket.setReceiveBufferSize(128 * 1024);
            rtpSocket.setSendBufferSize(128 * 1024);

            // 启动采集线程（麦克风 → RTP）
            captureThread = new Thread(this::captureLoop, "Audio-Capture");
            captureThread.setDaemon(true);
            captureThread.start();

            // 启动播放线程（RTP → 扬声器）
            playbackThread = new Thread(this::playbackLoop, "Audio-Playback");
            playbackThread.setDaemon(true);
            playbackThread.start();

            Log.i(TAG, "音频引擎已启动: local=" + localRtpPort
                    + ", remote=" + remoteIp + ":" + remoteRtpPort);
        } catch (Exception e) {
            Log.e(TAG, "音频引擎启动失败", e);
            running.set(false);
        }
    }

    /**
     * 停止音频引擎
     */
    public void stop() {
        running.set(false);

        if (rtpSocket != null && !rtpSocket.isClosed()) {
            rtpSocket.close();
        }
        rtpSocket = null;

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }

        Log.i(TAG, "音频引擎已停止: sent=" + packetsSent + " recv=" + packetsReceived);
    }

    public void setMute(boolean mute) {
        muted.set(mute);
        Log.d(TAG, "静音: " + mute);
    }

    public boolean isMuted() {
        return muted.get();
    }

    // ===== 采集线程：麦克风 PCM → G.711 μ-law → RTP → UDP =====

    private void captureLoop() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, FRAME_SIZE * 4);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 初始化失败");
            return;
        }

        audioRecord.startRecording();
        short[] pcmBuf = new short[FRAME_SIZE];
        byte[] payload = new byte[FRAME_SIZE]; // 预分配 payload 缓冲
        int seqNum = 0;
        int timestamp = 0;

        Log.i(TAG, "[采集] 启动 → " + remoteIp + ":" + remoteRtpPort);

        while (running.get()) {
            int read = audioRecord.read(pcmBuf, 0, FRAME_SIZE);
            if (read <= 0) continue;

            if (muted.get()) {
                timestamp += read;
                seqNum++;
                continue;
            }

            try {
                // PCM → G.711 μ-law
                for (int i = 0; i < read; i++) {
                    payload[i] = linearToUlaw(pcmBuf[i]);
                }

                // 构造 RTP 包
                byte[] rtpPacket = buildRtpPacket(seqNum, timestamp, payload, read);

                // UDP 发送（使用缓存的 InetAddress）
                rtpSocket.send(new DatagramPacket(rtpPacket, rtpPacket.length,
                        remoteAddr, remoteRtpPort));

                packetsSent++;
                seqNum++;
                timestamp += read;

                if (packetsSent <= 5) {
                    Log.i(TAG, "[采集] #" + packetsSent + ": " + rtpPacket.length + "B");
                } else if (packetsSent % 500 == 0) {
                    Log.d(TAG, "[采集] sent=" + packetsSent);
                }
            } catch (Exception e) {
                if (running.get()) {
                    Log.e(TAG, "[采集] 发送失败", e);
                }
            }
        }
    }

    // ===== 播放线程：UDP → RTP → G.711 μ-law → PCM → 扬声器 =====

    private void playbackLoop() {
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // 播放缓冲适当增大，减少断续
        int bufSize = Math.max(minBuf, FRAME_SIZE * 8);

        audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);

        audioTrack.play();

        byte[] recvBuf = new byte[RTP_HEADER_SIZE + FRAME_SIZE + 100];

        Log.i(TAG, "[播放] 启动, port=" + localRtpPort);

        while (running.get()) {
            try {
                DatagramPacket dp = new DatagramPacket(recvBuf, recvBuf.length);
                rtpSocket.receive(dp);

                if (dp.getLength() <= RTP_HEADER_SIZE) continue;

                packetsReceived++;

                // 提取 RTP payload
                int payloadLen = dp.getLength() - RTP_HEADER_SIZE;

                // G.711 μ-law → PCM（直接解码，不额外分配 payload 数组）
                short[] pcmSamples = new short[payloadLen];
                for (int i = 0; i < payloadLen; i++) {
                    pcmSamples[i] = ulawToLinear(recvBuf[RTP_HEADER_SIZE + i]);
                }

                // 播放
                audioTrack.write(pcmSamples, 0, pcmSamples.length);

                if (packetsReceived <= 5) {
                    Log.i(TAG, "[播放] #" + packetsReceived
                            + ": " + payloadLen + "B from " + dp.getAddress());
                } else if (packetsReceived % 500 == 0) {
                    Log.d(TAG, "[播放] recv=" + packetsReceived);
                }

            } catch (java.net.SocketTimeoutException ignored) {
                // 超时正常，继续接收
            } catch (Exception e) {
                if (running.get()) {
                    Log.e(TAG, "[播放] 接收失败", e);
                }
            }
        }
    }

    // ===== RTP 打包 =====

    private byte[] buildRtpPacket(int seq, int ts, byte[] payload, int len) {
        byte[] packet = new byte[RTP_HEADER_SIZE + len];

        // V=2, P=0, X=0, CC=0
        packet[0] = (byte) 0x80;
        // M=0, PT=0 (PCMU)
        packet[1] = (byte) RTP_PAYLOAD_TYPE;
        // Sequence number
        packet[2] = (byte) ((seq >> 8) & 0xFF);
        packet[3] = (byte) (seq & 0xFF);
        // Timestamp
        packet[4] = (byte) ((ts >> 24) & 0xFF);
        packet[5] = (byte) ((ts >> 16) & 0xFF);
        packet[6] = (byte) ((ts >> 8) & 0xFF);
        packet[7] = (byte) (ts & 0xFF);
        // SSRC
        packet[8] = (byte) ((ssrc >> 24) & 0xFF);
        packet[9] = (byte) ((ssrc >> 16) & 0xFF);
        packet[10] = (byte) ((ssrc >> 8) & 0xFF);
        packet[11] = (byte) (ssrc & 0xFF);

        // Payload
        System.arraycopy(payload, 0, packet, RTP_HEADER_SIZE, len);
        return packet;
    }

    // ===== G.711 μ-law 编解码（ITU-T G.711）=====

    /** PCM 16-bit → μ-law 8-bit */
    private static byte linearToUlaw(short sample) {
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = (short) -sample;
        if (sample > 32635) sample = 32635;

        sample = (short) (sample + 0x84);
        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; exponent--, mask >>= 1) {}

        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        byte ulawByte = (byte) ~(sign | (exponent << 4) | mantissa);
        return ulawByte;
    }

    /** μ-law 8-bit → PCM 16-bit */
    private static short ulawToLinear(byte ulawByte) {
        int ulaw = ~ulawByte & 0xFF;
        int sign = ulaw & 0x80;
        int exponent = (ulaw >> 4) & 0x07;
        int mantissa = ulaw & 0x0F;

        int sample = ((mantissa << 3) + 0x84) << exponent;
        sample -= 0x84;

        return (short) (sign != 0 ? -sample : sample);
    }
}
