package com.example.myapplication.conference;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 会议音频处理器
 * 通过 WebSocket 传输 PCM Base64（与 PC 端兼容）
 *
 * 采集管线：AudioRecord(16000Hz, mono, 16bit) → PCM bytes → Base64.NO_WRAP → WebSocket
 * 播放管线：WebSocket → Base64 decode → PCM bytes → 播放队列 → 播放线程 → AudioTrack(per-user)
 */
public class ConferenceAudioHandler {

    private static final String TAG = "ConferenceAudio";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FRAME_SIZE = 1280; // 640 samples * 2 bytes = 20ms at 16kHz (matches PC)

    private ConferenceMediaClient mediaClient;
    private AudioRecord audioRecord;
    private volatile boolean capturing = false;
    private volatile boolean muted = false;
    private Thread captureThread;

    // Per-user audio playback
    private final ConcurrentHashMap<Long, AudioTrack> audioTracks = new ConcurrentHashMap<>();

    // 播放队列 + 播放线程：避免在 decodeExecutor 线程直接调 AudioTrack.write() 导致 native crash
    private final LinkedBlockingQueue<AudioFrame> playbackQueue = new LinkedBlockingQueue<>(100);
    private volatile boolean playing = false;
    private Thread playbackThread;

    private static class AudioFrame {
        final long userId;
        final byte[] pcmData;
        AudioFrame(long userId, byte[] pcmData) {
            this.userId = userId;
            this.pcmData = pcmData;
        }
    }

    public void init(ConferenceMediaClient client) {
        this.mediaClient = client;
        startPlaybackThread();
    }

    private void startPlaybackThread() {
        playing = true;
        playbackThread = new Thread(() -> {
            Log.i(TAG, "Audio playback thread started");
            while (playing) {
                try {
                    AudioFrame frame = playbackQueue.take();
                    AudioTrack track = getOrCreateTrack(frame.userId);
                    if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED
                            && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.write(frame.pcmData, 0, frame.pcmData.length);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    Log.e(TAG, "Playback thread error", t);
                }
            }
            Log.i(TAG, "Audio playback thread stopped");
        }, "ConferenceAudioPlayback");
        playbackThread.start();
    }

    /**
     * 启动麦克风采集
     */
    public void startCapture() {
        if (capturing) return;

        int bufSize = Math.max(
                AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT),
                FRAME_SIZE * 4
        );

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, bufSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }

        capturing = true;
        audioRecord.startRecording();

        captureThread = new Thread(() -> {
            byte[] buffer = new byte[FRAME_SIZE];
            Log.i(TAG, "Audio capture started");

            while (capturing) {
                int read = audioRecord.read(buffer, 0, FRAME_SIZE);
                if (read > 0 && !muted && mediaClient != null && mediaClient.isConnected()) {
                    String base64Data = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP);
                    mediaClient.sendAudioFrame(base64Data, read);
                }
            }

            Log.i(TAG, "Audio capture stopped");
        }, "ConferenceAudioCapture");
        captureThread.start();
    }

    /**
     * 停止麦克风采集
     */
    public void stopCapture() {
        capturing = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop AudioRecord failed", e);
            }
            audioRecord = null;
        }
    }

    /**
     * 接收远端音频帧 — 只入队，不直接操作 AudioTrack
     * 由播放线程统一处理，避免 native crash
     */
    public void onAudioFrameReceived(long userId, String base64Data) {
        try {
            if (base64Data == null || base64Data.isEmpty()) return;
            byte[] pcmData = Base64.decode(base64Data, Base64.NO_WRAP);
            if (pcmData == null || pcmData.length == 0) return;

            // offer 不阻塞，队列满则丢弃（比崩溃好）
            if (!playbackQueue.offer(new AudioFrame(userId, pcmData))) {
                // 队列满，丢弃最老的帧
                playbackQueue.poll();
                playbackQueue.offer(new AudioFrame(userId, pcmData));
            }
        } catch (Throwable t) {
            Log.e(TAG, "onAudioFrameReceived failed: userId=" + userId, t);
        }
    }

    private synchronized AudioTrack getOrCreateTrack(long userId) {
        AudioTrack track = audioTracks.get(userId);
        if (track != null) return track;

        int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioTrack not supported: getMinBufferSize returned " + minBufSize);
            return null;
        }
        int bufSize = Math.max(minBufSize, FRAME_SIZE * 4);

        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .setEncoding(AUDIO_FORMAT)
                            .build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Throwable e1) {
            Log.w(TAG, "VOICE_COMMUNICATION AudioTrack failed, falling back to MEDIA", e1);
            try {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(CHANNEL_CONFIG_OUT)
                                .setEncoding(AUDIO_FORMAT)
                                .build())
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } catch (Throwable e2) {
                Log.e(TAG, "AudioTrack creation completely failed", e2);
                return null;
            }
        }

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack not initialized, releasing");
            track.release();
            return null;
        }

        track.play();
        audioTracks.put(userId, track);
        Log.i(TAG, "Created AudioTrack for user: " + userId);
        return track;
    }

    /**
     * 移除某用户的音频播放器
     */
    public void removeUser(long userId) {
        AudioTrack track = audioTracks.remove(userId);
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "Release AudioTrack failed: userId=" + userId, e);
            }
        }
    }

    public void setMute(boolean mute) {
        this.muted = mute;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * 释放所有资源
     */
    public void release() {
        stopCapture();
        playing = false;
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        playbackQueue.clear();
        for (AudioTrack track : audioTracks.values()) {
            try {
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "Release AudioTrack failed", e);
            }
        }
        audioTracks.clear();
        Log.i(TAG, "All audio resources released");
    }
}
