package com.sip.client.ui.controller;

import com.sip.client.call.SipCallManager;
import com.sip.client.media.MediaManager;
import com.sip.client.media.video.VideoRenderer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 通话窗口控制器
 * 负责音视频通话的UI控制
 */
@Slf4j
public class CallWindowController {

    @FXML
    private Label lblCallWith;

    @FXML
    private Label lblCallStatus;

    @FXML
    private Label lblCallDuration;

    @FXML
    private Canvas remoteVideoCanvas;

    @FXML
    private Canvas localVideoCanvas;

    @FXML
    private VBox audioOnlyPlaceholder;

    @FXML
    private Button btnMute;

    @FXML
    private Button btnHangup;

    @FXML
    private Button btnCamera;

    // 通话相关
    private String targetUsername;
    private String callType; // "audio" or "video"
    private boolean isCaller; // 是否为主叫方
    private String callId;

    // 管理器
    private SipCallManager callManager;
    private MediaManager mediaManager;

    // 渲染器
    private VideoRenderer remoteRenderer;
    private VideoRenderer localRenderer;

    // 状态
    private boolean isMuted = false;
    private boolean isCameraOff = false;

    // 通话时长计时器
    private Timer durationTimer;
    private int callDurationSeconds = 0;

    // 窗口
    private Stage stage;

    // ✅ 保存原始监听器，以便关闭时恢复
    private SipCallManager.CallEventListener originalListener;

    /**
     * 初始化
     */
    @FXML
    public void initialize() {
        log.info("通话窗口初始化");

        // 初始化渲染器
        remoteRenderer = new VideoRenderer();
        remoteRenderer.initialize(remoteVideoCanvas, 640, 480);

        localRenderer = new VideoRenderer();
        localRenderer.initialize(localVideoCanvas, 200, 150);

        // 获取管理器实例
        callManager = SipCallManager.getInstance();
        mediaManager = MediaManager.getInstance();

        // ✅ 保存当前监听器（MainController的监听器）
        originalListener = callManager.getCurrentCallEventListener();
        log.info("✅ 已保存原始CallEventListener: {}", originalListener != null ? "存在" : "null");

        // 设置通话事件监听
        setupCallEventListener();
    }

    /**
     * 设置通话参数
     */
    public void setCallParams(String targetUsername, String callType, boolean isCaller) {
        this.targetUsername = targetUsername;
        this.callType = callType;
        this.isCaller = isCaller;

        // ✅ 提取SIP号码用于显示（例如：user100 → 100）
        String displayName = targetUsername;
        if (targetUsername != null && targetUsername.startsWith("user")) {
            displayName = targetUsername.replace("user", "");
        }

        // 更新UI - 显示SIP号码而不是用户名
        if (isCaller) {
            lblCallWith.setText("拨打给 " + displayName);
        } else {
            lblCallWith.setText(displayName + " 来电");
        }

        // 根据通话类型调整UI
        if ("audio".equals(callType)) {
            // 语音通话：隐藏视频区域，显示占位符
            audioOnlyPlaceholder.setVisible(true);
            btnCamera.setVisible(false);
        } else {
            // 视频通话
            audioOnlyPlaceholder.setVisible(false);
            btnCamera.setVisible(true);
        }
    }

    /**
     * 设置窗口引用
     */
    public void setStage(Stage stage) {
        this.stage = stage;

        // 窗口关闭时挂断通话
        stage.setOnCloseRequest(event -> {
            handleHangup();
        });
    }

    /**
     * 设置CallId（被叫方需要）
     */
    public void setCallId(String callId) {
        this.callId = callId;
    }

    /**
     * 发起呼叫（主叫方）
     */
    public void makeCall() {
        try {
            lblCallStatus.setText("呼叫中...");

            // 获取本地媒体端口
            int audioPort = mediaManager.getCurrentAudioPort();
            int videoPort = mediaManager.getCurrentVideoPort();

            log.info("发起{}通话给 {}, 音频端口={}, 视频端口={}",
                callType, targetUsername, audioPort, videoPort);

            // ✅ 提取SIP号码（例如：user100 → 100）
            String sipNumber = targetUsername.replace("user", "");
            log.info("转换用户名为SIP号码: {} → {}", targetUsername, sipNumber);

            // 发起SIP呼叫
            callManager.makeCall(sipNumber, callType, audioPort, videoPort);

        } catch (Exception e) {
            log.error("发起通话失败", e);
            Platform.runLater(() -> {
                lblCallStatus.setText("呼叫失败: " + e.getMessage());
            });
        }
    }

    /**
     * 接听呼叫（被叫方）
     */
    public void answerCall() {
        try {
            lblCallStatus.setText("接听中...");

            // 获取本地媒体端口
            int audioPort = mediaManager.getCurrentAudioPort();
            int videoPort = mediaManager.getCurrentVideoPort();

            log.info("接听{}通话，CallId={}, 音频端口={}, 视频端口={}",
                callType, callId, audioPort, videoPort);

            // 接听SIP呼叫
            callManager.answerCall(callId, audioPort, videoPort);

        } catch (Exception e) {
            log.error("接听通话失败", e);
            Platform.runLater(() -> {
                lblCallStatus.setText("接听失败: " + e.getMessage());
            });
        }
    }

    /**
     * 设置通话事件监听
     */
    private void setupCallEventListener() {
        callManager.setCallEventListener(new SipCallManager.CallEventListener() {
            @Override
            public void onCalling(String targetUsername) {
                Platform.runLater(() -> {
                    lblCallStatus.setText("正在呼叫 " + targetUsername + "...");
                });
            }

            @Override
            public void onIncomingCall(String callId, String callerUri, String callType) {
                // 来电通知（这个回调在MainController中处理）
                log.info("收到来电: callId={}, caller={}, type={}", callId, callerUri, callType);
            }

            @Override
            public void onRinging() {
                Platform.runLater(() -> {
                    lblCallStatus.setText("对方振铃中...");
                });
            }

            @Override
            public void onCallEstablished(Map<String, Object> remoteMediaInfo) {
                Platform.runLater(() -> {
                    log.info("通话建立成功，远程媒体信息: {}", remoteMediaInfo);
                    lblCallStatus.setText("通话中");

                    // 启动媒体传输
                    startMedia(remoteMediaInfo);

                    // 启动通话时长计时器
                    startDurationTimer();
                });
            }

            @Override
            public void onCallEnded() {
                Platform.runLater(() -> {
                    log.info("通话已结束");
                    lblCallStatus.setText("通话已结束");

                    // 停止媒体
                    stopMedia();

                    // 停止计时器
                    stopDurationTimer();

                    // ✅ 恢复原始监听器（MainController的监听器）
                    restoreOriginalListener();

                    // 2秒后关闭窗口
                    Timer closeTimer = new Timer();
                    closeTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                if (stage != null) {
                                    stage.close();
                                }
                            });
                        }
                    }, 2000);
                });
            }

            @Override
            public void onCallFailed(String reason) {
                Platform.runLater(() -> {
                    log.error("通话失败: {}", reason);
                    lblCallStatus.setText("通话失败: " + reason);

                    // 停止媒体
                    stopMedia();

                    // ✅ 恢复原始监听器（MainController的监听器）
                    restoreOriginalListener();

                    // 3秒后关闭窗口
                    Timer closeTimer = new Timer();
                    closeTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                if (stage != null) {
                                    stage.close();
                                }
                            });
                        }
                    }, 3000);
                });
            }
        });
    }

    /**
     * 启动媒体传输
     */
    private void startMedia(Map<String, Object> remoteMediaInfo) {
        try {
            if ("video".equals(callType)) {
                // 启动视频通话
                mediaManager.startVideoCall(remoteMediaInfo, remoteRenderer);
                log.info("✅ 视频通话媒体已启动");
            } else {
                // 启动音频通话
                mediaManager.startAudioCall(remoteMediaInfo);
                log.info("✅ 音频通话媒体已启动");
            }
        } catch (Exception e) {
            log.error("❌ 启动媒体失败", e);
            Platform.runLater(() -> {
                lblCallStatus.setText("媒体启动失败: " + e.getMessage());
            });
        }
    }

    /**
     * 停止媒体传输
     */
    private void stopMedia() {
        try {
            mediaManager.stopMedia();
            log.info("媒体已停止");
        } catch (Exception e) {
            log.error("停止媒体失败", e);
        }
    }

    /**
     * 启动通话时长计时器
     */
    private void startDurationTimer() {
        callDurationSeconds = 0;
        durationTimer = new Timer();
        durationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                callDurationSeconds++;
                Platform.runLater(() -> {
                    int minutes = callDurationSeconds / 60;
                    int seconds = callDurationSeconds % 60;
                    lblCallDuration.setText(String.format("%02d:%02d", minutes, seconds));
                });
            }
        }, 1000, 1000);
    }

    /**
     * 停止通话时长计时器
     */
    private void stopDurationTimer() {
        if (durationTimer != null) {
            durationTimer.cancel();
            durationTimer = null;
        }
    }

    /**
     * 处理静音按钮
     */
    @FXML
    public void handleMute() {
        isMuted = !isMuted;
        if (isMuted) {
            btnMute.setText("🔊 取消静音");
            // TODO: 实现静音功能（停止音频采集）
            log.info("静音已开启");
        } else {
            btnMute.setText("🔇 静音");
            // TODO: 实现取消静音（恢复音频采集）
            log.info("静音已关闭");
        }
    }

    /**
     * 处理挂断按钮
     */
    @FXML
    public void handleHangup() {
        log.info("用户点击挂断按钮");
        lblCallStatus.setText("挂断中...");

        // 发送BYE信令
        callManager.hangupCall();

        // 停止媒体
        stopMedia();

        // 停止计时器
        stopDurationTimer();

        // ✅ 恢复原始监听器（MainController的监听器）
        restoreOriginalListener();
    }

    /**
     * ✅ 恢复原始监听器（MainController的监听器）
     */
    private void restoreOriginalListener() {
        if (originalListener != null) {
            log.info("✅ 恢复原始CallEventListener");
            callManager.setCallEventListener(originalListener);
        } else {
            log.warn("⚠️ 原始CallEventListener为null，无法恢复");
        }
    }

    /**
     * 处理摄像头按钮
     */
    @FXML
    public void handleCamera() {
        isCameraOff = !isCameraOff;
        if (isCameraOff) {
            btnCamera.setText("📹 打开摄像头");
            // TODO: 实现关闭摄像头（停止视频采集）
            log.info("摄像头已关闭");
        } else {
            btnCamera.setText("📹 关闭摄像头");
            // TODO: 实现打开摄像头（恢复视频采集）
            log.info("摄像头已打开");
        }
    }
}
