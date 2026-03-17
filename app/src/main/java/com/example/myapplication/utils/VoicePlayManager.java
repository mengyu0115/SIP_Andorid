package com.example.myapplication.utils;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.IOException;

/**
 * 语音播放管理器（单例）
 */
public class VoicePlayManager {
    private static final String TAG = "VoicePlayManager";
    private static VoicePlayManager instance;

    private MediaPlayer mediaPlayer;
    private String currentPlayingUrl;  // 当前正在播放的语音URL
    private PlayCallback callback;
    private Handler mainHandler;

    public interface PlayCallback {
        void onStart();
        void onCompletion();
        void onError(String error);
    }

    private VoicePlayManager() {
        mainHandler = new Handler(Looper.getMainLooper());
        try {
            mediaPlayer = new MediaPlayer();
            setupListeners(mediaPlayer);
        } catch (Exception e) {
            Log.e(TAG, "初始化MediaPlayer失败", e);
        }
    }

    /**
     * 设置 MediaPlayer 监听器
     */
    private void setupListeners(MediaPlayer mp) {
        mp.setOnCompletionListener(mediaPlayerObj -> {
            Log.i(TAG, "播放完成");
            final String url = currentPlayingUrl;
            currentPlayingUrl = null;

            // 播放完成后重置 MediaPlayer
            try {
                mediaPlayerObj.reset();
                Log.i(TAG, "播放完成后已重置MediaPlayer");
            } catch (Exception e) {
                Log.e(TAG, "重置MediaPlayer失败", e);
                recreateMediaPlayer();
            }

            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onCompletion();
                    } catch (Exception e) {
                        Log.e(TAG, "onCompletion回调失败", e);
                    }
                });
            }
        });

        mp.setOnErrorListener((mediaPlayerObj, what, extra) -> {
            Log.e(TAG, "播放错误: what=" + what + ", extra=" + extra);
            final String url = currentPlayingUrl;
            currentPlayingUrl = null;

            // 错误后重新创建 MediaPlayer
            mainHandler.post(() -> recreateMediaPlayer());

            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onError("播放失败 (错误码: " + what + ")");
                    } catch (Exception e) {
                        Log.e(TAG, "onError回调失败", e);
                    }
                });
            }
            return true;
        });
    }

    public static synchronized VoicePlayManager getInstance() {
        if (instance == null) {
            instance = new VoicePlayManager();
        }
        return instance;
    }

    /**
     * 播放语音文件
     * @param file 语音文件
     * @param callback 播放回调
     */
    public void play(File file, PlayCallback callback) {
        play(file.getAbsolutePath(), callback);
    }

    /**
     * 播放语音文件（通过文件路径）
     * @param filePath 文件路径
     * @param callback 播放回调
     */
    public void play(String filePath, PlayCallback callback) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "文件路径为空");
            if (callback != null) {
                mainHandler.post(() -> callback.onError("文件路径为空"));
            }
            return;
        }

        this.callback = callback;

        try {
            // 如果正在播放这个URL，则停止播放
            if (isPlaying(filePath)) {
                stop();
                return;
            }

            // 每次播放前都重新创建 MediaPlayer，确保状态正确
            recreateMediaPlayer();

            currentPlayingUrl = filePath;

            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.i(TAG, "开始播放: " + filePath);
            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onStart();
                    } catch (Exception e) {
                        Log.e(TAG, "onStart回调失败", e);
                    }
                });
            }

        } catch (IOException e) {
            Log.e(TAG, "播放失败", e);
            currentPlayingUrl = null;
            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onError("播放失败: " + e.getMessage());
                    } catch (Exception ex) {
                        Log.e(TAG, "onError回调失败", ex);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "播放异常", e);
            currentPlayingUrl = null;
            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onError("播放异常: " + e.getMessage());
                    } catch (Exception ex) {
                        Log.e(TAG, "onError回调失败", ex);
                    }
                });
            }
        }
    }

    /**
     * 播放网络语音（改为先下载再播放）
     * @param url 语音URL
     * @param callback 播放回调
     */
    public void playUrl(String url, PlayCallback callback) {
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "URL为空");
            if (callback != null) {
                mainHandler.post(() -> callback.onError("URL为空"));
            }
            return;
        }

        // 如果正在播放这个URL，则停止播放
        if (isPlaying(url)) {
            stop();
            return;
        }

        // 直接使用URL播放（MediaPlayer支持网络流）
        this.callback = callback;

        try {
            // 每次播放前都重新创建 MediaPlayer，确保状态正确
            recreateMediaPlayer();

            currentPlayingUrl = url;

            mediaPlayer.setDataSource(url);
            mediaPlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.prepareAsync();

            mediaPlayer.setOnPreparedListener(mp -> {
                try {
                    mp.start();
                    Log.i(TAG, "开始播放网络语音: " + url);
                    if (callback != null) {
                        mainHandler.post(() -> {
                            try {
                                callback.onStart();
                            } catch (Exception e) {
                                Log.e(TAG, "onStart回调失败", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "播放失败", e);
                    currentPlayingUrl = null;
                    if (callback != null) {
                        mainHandler.post(() -> {
                            try {
                                callback.onError("播放失败: " + e.getMessage());
                            } catch (Exception ex) {
                                Log.e(TAG, "onError回调失败", ex);
                            }
                        });
                    }
                }
            });

        } catch (IOException e) {
            Log.e(TAG, "准备播放失败", e);
            currentPlayingUrl = null;
            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onError("准备播放失败: " + e.getMessage());
                    } catch (Exception ex) {
                        Log.e(TAG, "onError回调失败", ex);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "播放异常", e);
            currentPlayingUrl = null;
            if (callback != null) {
                mainHandler.post(() -> {
                    try {
                        callback.onError("播放异常: " + e.getMessage());
                    } catch (Exception ex) {
                        Log.e(TAG, "onError回调失败", ex);
                    }
                });
            }
        }
    }

    /**
     * 重新创建 MediaPlayer
     */
    private void recreateMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.e(TAG, "释放旧MediaPlayer失败", e);
                }
            }
            mediaPlayer = new MediaPlayer();

            // 重新设置监听器
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "播放完成");
                final String url = currentPlayingUrl;
                currentPlayingUrl = null;
                if (callback != null) {
                    mainHandler.post(() -> {
                        try {
                            callback.onCompletion();
                        } catch (Exception e) {
                            Log.e(TAG, "onCompletion回调失败", e);
                        }
                    });
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "播放错误: what=" + what + ", extra=" + extra);
                final String url = currentPlayingUrl;
                currentPlayingUrl = null;
                if (callback != null) {
                    mainHandler.post(() -> {
                        try {
                            callback.onError("播放失败 (错误码: " + what + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "onError回调失败", e);
                        }
                    });
                }
                return true;
            });

            Log.i(TAG, "MediaPlayer已重新创建");
        } catch (Exception e) {
            Log.e(TAG, "重新创建MediaPlayer失败", e);
        }
    }

    /**
     * 停止播放
     */
    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                currentPlayingUrl = null;
                Log.i(TAG, "停止播放并重置");
            } catch (Exception e) {
                Log.e(TAG, "停止播放失败", e);
                currentPlayingUrl = null;
                // 发生异常，重新创建 MediaPlayer
                recreateMediaPlayer();
            }
        }
    }

    /**
     * 暂停播放
     */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                Log.i(TAG, "暂停播放");
            } catch (Exception e) {
                Log.e(TAG, "暂停播放失败", e);
            }
        }
    }

    /**
     * 继续播放
     */
    public void resume() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.start();
                Log.i(TAG, "继续播放");
            } catch (Exception e) {
                Log.e(TAG, "继续播放失败", e);
            }
        }
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (Exception e) {
                Log.e(TAG, "检查播放状态失败", e);
                return false;
            }
        }
        return false;
    }

    /**
     * 是否正在播放指定的语音
     */
    public boolean isPlaying(String url) {
        return isPlaying() && currentPlayingUrl != null && currentPlayingUrl.equals(url);
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                Log.e(TAG, "释放MediaPlayer失败", e);
            }
        }
        currentPlayingUrl = null;
        callback = null;
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "获取播放位置失败", e);
            }
        }
        return 0;
    }

    /**
     * 获取总时长（毫秒）
     */
    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {
                Log.e(TAG, "获取总时长失败", e);
            }
        }
        return 0;
    }
}
