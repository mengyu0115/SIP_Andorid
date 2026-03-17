package com.example.myapplication.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 语音录音管理器
 */
public class VoiceRecordManager {
    private static final String TAG = "VoiceRecordManager";
    private static VoiceRecordManager instance;

    private MediaRecorder mediaRecorder;
    private String currentFilePath;
    private long startTime;
    private RecordState state = RecordState.IDLE;

    public enum RecordState {
        IDLE,       // 空闲
        RECORDING,  // 录音中
        PAUSED      // 暂停
    }

    public interface RecordCallback {
        void onStart();
        void onProgress(int duration);
        void onStop(int duration, String filePath);
        void onError(String error);
        void onCancel();
    }

    private RecordCallback callback;

    private VoiceRecordManager() {}

    public static synchronized VoiceRecordManager getInstance() {
        if (instance == null) {
            instance = new VoiceRecordManager();
        }
        return instance;
    }

    /**
     * 开始录音
     */
    public void startRecord(Context context, RecordCallback callback) {
        if (state != RecordState.IDLE) {
            Log.w(TAG, "当前不是空闲状态，无法开始录音");
            return;
        }

        this.callback = callback;
        try {
            // 创建录音文件
            String fileName = "voice_" + UUID.randomUUID().toString() + ".m4a";
            File audioDir = new File(context.getExternalCacheDir(), "audio");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            File audioFile = new File(audioDir, fileName);
            currentFilePath = audioFile.getAbsolutePath();

            // 配置 MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentFilePath);

            // 设置音频参数（提高音质）
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);

            mediaRecorder.prepare();
            mediaRecorder.start();

            startTime = System.currentTimeMillis();
            state = RecordState.RECORDING;

            if (callback != null) {
                callback.onStart();
            }

            // 启动进度更新
            startProgressUpdate();

        } catch (IOException e) {
            Log.e(TAG, "开始录音失败", e);
            state = RecordState.IDLE;
            if (callback != null) {
                callback.onError("录音失败: " + e.getMessage());
            }
        }
    }

    /**
     * 停止录音
     */
    public void stopRecord() {
        if (state != RecordState.RECORDING) {
            return;
        }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            int duration = (int) ((System.currentTimeMillis() - startTime) / 1000);
            state = RecordState.IDLE;

            if (callback != null) {
                final RecordCallback cb = callback;
                final int finalDuration = duration;
                // 只有录音时长超过1秒才发送
                if (duration >= 1) {
                    runOnUiThread(() -> cb.onStop(finalDuration, currentFilePath));
                } else {
                    // 太短了，取消发送并删除文件
                    deleteRecordFile();
                    runOnUiThread(() -> cb.onCancel());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "停止录音失败", e);
            state = RecordState.IDLE;
            if (callback != null) {
                final RecordCallback cb = callback;
                runOnUiThread(() -> cb.onError("停止录音失败: " + e.getMessage()));
            }
        }
    }

    /**
     * 取消录音（上滑取消）
     */
    public void cancelRecord() {
        if (state != RecordState.RECORDING) {
            return;
        }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            deleteRecordFile();
            state = RecordState.IDLE;

            if (callback != null) {
                final RecordCallback cb = callback;
                runOnUiThread(() -> cb.onCancel());
            }

        } catch (Exception e) {
            Log.e(TAG, "取消录音失败", e);
            state = RecordState.IDLE;
        }
    }

    /**
     * 在主线程执行
     */
    private void runOnUiThread(Runnable action) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    /**
     * 获取当前录音时长（秒）
     */
    public int getCurrentDuration() {
        if (state == RecordState.RECORDING) {
            return (int) ((System.currentTimeMillis() - startTime) / 1000);
        }
        return 0;
    }

    /**
     * 获取当前状态
     */
    public RecordState getState() {
        return state;
    }

    /**
     * 是否正在录音
     */
    public boolean isRecording() {
        return state == RecordState.RECORDING;
    }

    /**
     * 删除录音文件
     */
    private void deleteRecordFile() {
        if (currentFilePath != null) {
            File file = new File(currentFilePath);
            if (file.exists()) {
                file.delete();
            }
            currentFilePath = null;
        }
    }

    /**
     * 启动进度更新
     */
    private void startProgressUpdate() {
        new Thread(() -> {
            while (state == RecordState.RECORDING) {
                try {
                    Thread.sleep(100);
                    int duration = getCurrentDuration();
                    if (callback != null) {
                        final int currentDuration = duration;
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> callback.onProgress(currentDuration));
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "释放 MediaRecorder 失败", e);
            }
            mediaRecorder = null;
        }
        state = RecordState.IDLE;
        callback = null;
    }
}
