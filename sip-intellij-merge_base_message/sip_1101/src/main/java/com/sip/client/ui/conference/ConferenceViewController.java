package com.sip.client.ui.conference;

import com.github.sarxos.webcam.Webcam;
import com.sip.client.SipConferenceManager;
import com.sip.client.config.SipConfig;
import com.sip.common.dto.ConferenceDTO;
import com.sip.common.dto.ParticipantDTO;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.AWTException;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * 会议窗口控制器 - 腾讯会议风格（深色主题）
 *
 * 特性：
 * - 真实摄像头集成（使用webcam-capture库）
 * - 屏幕共享功能（使用Robot）
 * - 3x3视频网格布局
 * - 右侧聊天面板
 * - 底部控制按钮
 *
 * @author SIP Team
 * @version 2.0
 */
public class ConferenceViewController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceViewController.class);

    // ========== FXML 元素 ==========

    @FXML
    private Label lblConferenceTitle;

    @FXML
    private Label lblConferenceTime;

    @FXML
    private Label lblParticipantCount;

    @FXML
    private GridPane videoGridPane;

    @FXML
    private ScrollPane chatScrollPane;

    @FXML
    private VBox chatMessagesBox;

    @FXML
    private TextField chatInputField;

    @FXML
    private Button btnSendMessage;

    @FXML
    private VBox btnMicrophone;

    @FXML
    private Label lblMicIcon;

    @FXML
    private Label lblMicText;

    @FXML
    private VBox btnCamera;

    @FXML
    private Label lblCameraIcon;

    @FXML
    private Label lblCameraText;

    @FXML
    private VBox btnScreenShare;

    @FXML
    private Label lblScreenIcon;

    @FXML
    private Label lblScreenText;

    @FXML
    private Button btnEndConference;

    // ========== 业务对象 ==========

    private SipConferenceManager sipConferenceManager;
    private ConferenceMediaHandler mediaHandler;
    private ConferenceDTO currentConference;
    private ObservableList<ParticipantDTO> participants = FXCollections.observableArrayList();
    private ConferenceWebSocketClient webSocketClient;
    private ConferenceMediaWebSocketClient mediaWebSocketClient; // WebSocket媒体流客户端

    // ========== 状态变量 ==========

    // ⚡ CRITICAL FIX #10: 麦克风默认静音，第一次点击时启动
    // 之前：isMicMuted = false 导致第一次点击变成静音，第二次才启动
    // 现在：isMicMuted = true，第一次点击变成非静音并启动麦克风
    private boolean isMicMuted = true;
    private boolean isCameraOn = true;
    private boolean isScreenSharing = false;
    private boolean isRealMode = true; // true=真实SIP模式, false=演示模式
    private String currentUsername = "本地用户";
    private String currentUserId = "101"; // SIP账号
    private String sipDomain = SipConfig.getSipDomain();
    private String sipServer = SipConfig.getSipServerHost();
    private String authToken; // 认证Token
    private List<String> availableFriends = new ArrayList<>();

    // ========== 摄像头采集 ==========

    private Webcam webcam;
    private java.util.Timer cameraTimer;
    private volatile boolean isCameraCapturing = false;

    // ========== 音频采集 ==========

    private com.sip.client.media.audio.AudioCaptureHelper audioCaptureHelper;

    // ========== 音频播放 ==========

    private Map<String, com.sip.client.media.audio.AudioPlayerHelper> audioPlayerMap = new HashMap<>();

    // ========== 会议信息 ==========

    private String conferenceCode;  // 会议号（6位数字）
    private String conferenceTitle; // 会议标题

    // ========== 视频面板 ==========

    private List<VideoPanel> videoPanels = new ArrayList<>();
    private Map<String, VideoPanel> remotePanelMap = new HashMap<>(); // 用于快速查找远程参与者面板
    private Map<String, VideoPanel> remoteScreenSharePanels = new HashMap<>(); // 远程屏幕共享面板
    private Map<String, String> userNameMap = new HashMap<>(); // 用户ID到用户名的映射
    private VideoPanel localVideoPanel;
    private VideoPanel screenSharePanel;
    private int videoGridColumns = 3; // 3x3网格

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("====================================================");
        logger.info("会议控制器初始化 - 真实 SIP 模式");
        logger.info("====================================================");

        try {
            // 初始化媒体处理器
            mediaHandler = new ConferenceMediaHandler();
            mediaHandler.initialize();

            // 如果已经有用户ID，立即设置给mediaHandler
            if (currentUserId != null && !currentUserId.isEmpty()) {
                mediaHandler.setCurrentUserId(currentUserId);
            }

            // 初始化SIP会议管理器
            sipConferenceManager = new SipConferenceManager();

            // 绑定聊天输入框回车事件
            chatInputField.setOnAction(event -> handleSendMessage());

            // 注意：会议时间在joinConference时更新（使用服务器返回的创建时间）

            // 添加悬停效果到控制按钮
            setupButtonHoverEffects();

            logger.info("会议控制器初始化完成");

        } catch (Exception e) {
            logger.error("会议控制器初始化失败", e);
            Platform.runLater(() -> {
                showAlert("初始化失败: " + e.getMessage(), Alert.AlertType.ERROR);
            });
        }
    }

    /**
     * 设置按钮悬停效果
     */
    private void setupButtonHoverEffects() {
        setupButtonHover(btnMicrophone, "#3c3c3c");
        setupButtonHover(btnCamera, "#3c3c3c");
        setupButtonHover(btnScreenShare, "#3c3c3c");
    }

    private void setupButtonHover(VBox button, String hoverColor) {
        if (button == null) return;

        String defaultStyle = button.getStyle();
        button.setOnMouseEntered(e -> button.setStyle(defaultStyle + "-fx-background-color: " + hoverColor + ";"));
        button.setOnMouseExited(e -> button.setStyle(defaultStyle));
    }

    /**
     * 加入会议
     */
    public void joinConference(ConferenceDTO conference) {
        try {
            logger.info("====================================================");
            logger.info("加入会议: {} ({})", conference.getTitle(), conference.getConferenceUri());
            logger.info("会议号: {}", conferenceCode != null ? conferenceCode : "未设置");
            logger.info("模式: {}", isRealMode ? "真实 SIP 通信" : "演示模式");
            logger.info("====================================================");

            this.currentConference = conference;

            // 更新界面
            Platform.runLater(() -> {
                // 优先使用设置的会议标题，否则使用DTO中的标题
                String displayTitle = (conferenceTitle != null && !conferenceTitle.isEmpty())
                    ? conferenceTitle
                    : conference.getTitle();

                // 如果有会议号，显示完整信息
                if (conferenceCode != null && !conferenceCode.isEmpty()) {
                    lblConferenceTitle.setText(displayTitle + " (会议号: " + conferenceCode + ")");
                } else {
                    lblConferenceTitle.setText(displayTitle);
                }

                // 更新会议时间（基于服务器返回的创建时间）
                updateConferenceTime();

                updateParticipantCount();
            });

            // 连接WebSocket
            connectWebSocket(conference);

            if (isRealMode) {
                // ========== 真实 SIP 模式 ==========
                joinConferenceWithSIP(conference);
            } else {
                // ========== 演示模式 ==========
                joinConferenceDemoMode(conference);
            }

        } catch (Exception e) {
            logger.error("加入会议失败", e);
            Platform.runLater(() -> showAlert("加入会议失败: " + e.getMessage(), Alert.AlertType.ERROR));
        }
    }

    /**
     * 使用真实 SIP 通信加入会议
     * ⚠️ 修复：会议功能通过WebSocket实现，不依赖SIP服务器
     */
    private void joinConferenceWithSIP(ConferenceDTO conference) {
        try {
            logger.info("⚠️  会议功能使用WebSocket模式，跳过SIP初始化");
            logger.info("会议将完全通过WebSocket和后端服务器实现");

            // ✅ 直接创建本地视频面板，不等待SIP连接
            Platform.runLater(() -> {
                logger.info("创建本地视频面板（WebSocket模式）");
                createLocalVideoPanel();

                // 本地视频面板创建后，WebSocket的onConnected回调会自动发送JOIN_CONFERENCE消息
                logger.info("等待WebSocket连接完成...");
            });

            logger.info("会议模式：纯WebSocket（不使用SIP）");

        } catch (Exception e) {
            logger.error("初始化会议失败", e);
            Platform.runLater(() -> {
                showAlert("初始化失败: " + e.getMessage(), Alert.AlertType.ERROR);
            });
        }
    }

    /**
     * 演示模式加入会议
     */
    private void joinConferenceDemoMode(ConferenceDTO conference) {
        Platform.runLater(() -> {
            // 创建本地视频面板
            createLocalVideoPanel();

            // 添加模拟参与者
            addDemoParticipants();
        });

        logger.info("演示模式加入完成");
    }

    /**
     * 创建本地视频面板
     */
    private void createLocalVideoPanel() {
        // 默认摄像头关闭状态（不自动启动摄像头）
        localVideoPanel = new VideoPanel(currentUsername + " (您)", false, true);
        videoPanels.add(localVideoPanel);
        addVideoToGrid(localVideoPanel);

        // 默认摄像头是关闭的
        isCameraOn = false;
        Platform.runLater(() -> {
            lblCameraIcon.setText("📷");
            btnCamera.setStyle("-fx-background-color: #c83532; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;");
        });
    }

    /**
     * 添加模拟参与者
     */
    private void addDemoParticipants() {
        String[] demoNames = {"张三 (主持人)", "李四", "王五", "赵六"};
        for (String name : demoNames) {
            VideoPanel panel = new VideoPanel(name, true, false);
            videoPanels.add(panel);
            addVideoToGrid(panel);
        }
        updateParticipantCount();
    }

    /**
     * 创建远程参与者视频面板
     */
    private void createRemoteParticipantPanel(ParticipantDTO participant) {
        try {
            String displayName = participant.getUsername();
            String participantId = String.valueOf(participant.getUserId());

            // 检查是否已经存在该参与者的面板
            if (remotePanelMap.containsKey(participantId)) {
                logger.warn("参与者面板已存在，跳过创建: {}", participantId);
                return;
            }

            // 存储用户名到映射
            userNameMap.put(participantId, displayName);

            // 创建视频面板（摄像头功能已禁用，仅显示占位符）
            VideoPanel panel = new VideoPanel(displayName, false, false);
            panel.setId(participantId); // 设置ID用于后续查找
            videoPanels.add(panel);
            remotePanelMap.put(participantId, panel); // 添加到map中
            addVideoToGrid(panel);

            updateParticipantCount();
            logger.info("已创建远程参与者视频面板: {} ({})", displayName, participantId);

        } catch (Exception e) {
            logger.error("创建远程参与者面板失败", e);
        }
    }

    /**
     * 移除参与者视频面板
     */
    private void removeParticipantPanel(Long userId) {
        try {
            String participantId = String.valueOf(userId);

            // 停止该参与者的音频播放器
            com.sip.client.media.audio.AudioPlayerHelper audioPlayer = audioPlayerMap.remove(participantId);
            if (audioPlayer != null) {
                try {
                    audioPlayer.stop();
                    logger.info("已停止用户 {} 的音频播放器", participantId);
                } catch (Exception e) {
                    logger.error("停止音频播放器失败: userId={}", participantId, e);
                }
            }

            // 从map中查找并移除
            VideoPanel panelToRemove = remotePanelMap.remove(participantId);

            if (panelToRemove == null) {
                // 如果map中没有，尝试从列表中查找
                for (VideoPanel panel : videoPanels) {
                    if (participantId.equals(panel.getId())) {
                        panelToRemove = panel;
                        break;
                    }
                }
            }

            if (panelToRemove != null) {
                videoPanels.remove(panelToRemove);

                // 摄像头功能已禁用，不需要取消视频回调

                // 重新布局视频网格
                Platform.runLater(() -> {
                    videoGridPane.getChildren().clear();
                    for (int i = 0; i < videoPanels.size(); i++) {
                        addVideoToGrid(videoPanels.get(i));
                    }
                    updateParticipantCount();
                });

                logger.info("已移除参与者视频面板: {}", participantId);
            }

        } catch (Exception e) {
            logger.error("移除参与者面板失败", e);
        }
    }

    /**
     * 将视频面板添加到网格
     */
    private void addVideoToGrid(VideoPanel panel) {
        int index = videoGridPane.getChildren().size();
        int row = index / videoGridColumns;
        int col = index % videoGridColumns;
        videoGridPane.add(panel, col, row);
    }

    /**
     * 更新参与者数量
     * 只统计真实参与者，不包括屏幕共享面板
     */
    private void updateParticipantCount() {
        // 参与者数量 = 1个本地用户 + 远程参与者数量（不包括屏幕共享）
        int count = 1 + remotePanelMap.size();
        lblParticipantCount.setText(count + " 位参与者");
    }

    /**
     * 更新会议时间
     */
    private void updateConferenceTime() {
        if (currentConference != null && currentConference.getCreateTime() != null) {
            // 使用服务器返回的会议创建时间
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            String startTime = timeFormat.format(currentConference.getCreateTime());

            // 计算预计结束时间 (开始时间+30分钟)
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(currentConference.getCreateTime());
            calendar.add(Calendar.MINUTE, 30);
            String endTime = timeFormat.format(calendar.getTime());

            lblConferenceTime.setText(startTime + " - " + endTime);
        } else {
            // 降级：如果没有服务器时间，使用本地当前时间
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
            String startTime = timeFormat.format(new Date());
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, 30);
            String endTime = timeFormat.format(calendar.getTime());
            lblConferenceTime.setText(startTime + " - " + endTime);
        }
    }

    // ========== 控制按钮处理 ==========

    /**
     * 切换麦克风
     */
    @FXML
    private void handleToggleMicrophone() {
        isMicMuted = !isMicMuted;

        lblMicIcon.setText(isMicMuted ? "🔇" : "🎤");
        btnMicrophone.setStyle(isMicMuted ?
            "-fx-background-color: #c83532; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;" :
            "-fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;");

        // 控制音频采集
        if (!isMicMuted) {
            startMicrophone();
        } else {
            stopMicrophone();
        }

        // 通过WebSocket通知其他参与者麦克风状态变化
        sendMediaStatusUpdate();

        logger.info("麦克风状态: {}", isMicMuted ? "静音" : "开启");
    }

    /**
     * 启动麦克风采集
     */
    private void startMicrophone() {
        try {
            logger.info("启动麦克风");

            // 初始化音频采集助手
            if (audioCaptureHelper == null) {
                audioCaptureHelper = new com.sip.client.media.audio.AudioCaptureHelper();
            }

            // 启动音频采集，并设置回调
            audioCaptureHelper.startCapture((audioData, length) -> {
                // 通过WebSocket发送音频数据
                if (mediaWebSocketClient != null && mediaWebSocketClient.isConnected()) {
                    sendAudioFrame(audioData, length);
                }
            });

            logger.info("麦克风采集已启动");

        } catch (Exception e) {
            logger.error("启动麦克风失败", e);
            Platform.runLater(() -> {
                showAlert("启动麦克风失败: " + e.getMessage(), Alert.AlertType.ERROR);
            });
        }
    }

    /**
     * 停止麦克风采集
     */
    private void stopMicrophone() {
        try {
            logger.info("停止麦克风");

            if (audioCaptureHelper != null) {
                audioCaptureHelper.stopCapture();
            }

            logger.info("麦克风采集已停止");

        } catch (Exception e) {
            logger.error("停止麦克风失败", e);
        }
    }

    /**
     * 发送音频帧到WebSocket
     */
    private void sendAudioFrame(byte[] audioData, int length) {
        try {
            // Base64编码音频数据
            byte[] dataToSend = java.util.Arrays.copyOf(audioData, length);
            String base64Data = java.util.Base64.getEncoder().encodeToString(dataToSend);

            // 通过WebSocket客户端发送音频帧
            if (mediaWebSocketClient != null && mediaWebSocketClient.isConnected()) {
                mediaWebSocketClient.sendAudioFrame(base64Data, length);
                logger.trace("发送音频帧: {} bytes", length);
            }

        } catch (Exception e) {
            logger.error("发送音频帧失败", e);
        }
    }

    /**
     * 切换摄像头
     */
    @FXML
    private void handleToggleCamera() {
        // 如果正在屏幕共享，不能打开摄像头
        if (isScreenSharing) {
            logger.warn("屏幕共享开启时无法打开摄像头");
            return;
        }

        isCameraOn = !isCameraOn;

        btnCamera.setStyle(isCameraOn ?
            "-fx-background-color: #2196f3; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;" :
            "-fx-background-color: #c83532; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;");

        if (isCameraOn) {
            startCamera();
        } else {
            stopCamera();
        }

        // 通过WebSocket通知其他参与者摄像头状态变化
        sendMediaStatusUpdate();

        logger.info("摄像头状态: {}", isCameraOn ? "开启" : "关闭");
    }

    /**
     * 启动摄像头
     */
    private void startCamera() {
        try {
            logger.info("启动摄像头");

            if (localVideoPanel != null) {
                localVideoPanel.setVideoEnabled(true);
            }

            // 初始化摄像头（640x480分辨率）
            if (webcam == null) {
                webcam = Webcam.getDefault();
                if (webcam == null) {
                    logger.error("未找到可用的摄像头");
                    Platform.runLater(() -> {
                        showAlert("未找到可用的摄像头", Alert.AlertType.ERROR);
                    });
                    return;
                }

                // 设置摄像头分辨率为640x480
                Dimension webcamSize = new Dimension(640, 480);
                webcam.setViewSize(webcamSize);
                webcam.open();
                logger.info("摄像头已打开: {}x{}", webcamSize.width, webcamSize.height);
            }

            // 启动摄像头采集定时器（15 FPS）
            isCameraCapturing = true;
            cameraTimer = new java.util.Timer("CameraCapture", true);
            cameraTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (isCameraCapturing && webcam != null && webcam.isOpen()) {
                        captureAndSendCameraFrame();
                    }
                }
            }, 0, 67); // 15 FPS (1000ms / 15 ≈ 67ms)

            logger.info("摄像头采集已启动（15 FPS）");

        } catch (Exception e) {
            logger.error("启动摄像头失败", e);
            Platform.runLater(() -> {
                showAlert("启动摄像头失败: " + e.getMessage(), Alert.AlertType.ERROR);
            });
        }
    }

    /**
     * 捕获并发送摄像头帧
     */
    private void captureAndSendCameraFrame() {
        try {
            if (webcam == null || !webcam.isOpen()) {
                return;
            }

            // 从摄像头获取图像
            BufferedImage image = webcam.getImage();
            if (image == null) {
                return;
            }

            // 更新本地视频面板显示摄像头内容
            if (localVideoPanel != null) {
                Image fxImage = SwingFXUtils.toFXImage(image, null);
                Platform.runLater(() -> {
                    localVideoPanel.setImage(fxImage);
                });
            }

            // 通过WebSocket发送摄像头帧
            if (mediaWebSocketClient != null && mediaWebSocketClient.isConnected()) {
                mediaWebSocketClient.sendVideoFrameDirect(image);
                logger.trace("发送摄像头帧: {}x{}", image.getWidth(), image.getHeight());
            }

        } catch (Exception e) {
            logger.error("捕获摄像头帧失败", e);
        }
    }

    /**
     * 停止摄像头
     */
    private void stopCamera() {
        try {
            logger.info("停止摄像头");

            // 停止采集
            isCameraCapturing = false;

            // 停止定时器
            if (cameraTimer != null) {
                cameraTimer.cancel();
                cameraTimer = null;
            }

            // 关闭摄像头
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
                logger.info("摄像头已关闭");
            }

            webcam = null;

            // 更新本地视频面板
            if (localVideoPanel != null) {
                localVideoPanel.setVideoEnabled(false);
            }

            if (mediaWebSocketClient != null) {
                mediaWebSocketClient.stopVideoSending();
                logger.info("摄像头视频发送已停止");
            }

        } catch (Exception e) {
            logger.error("停止摄像头失败", e);
        }
    }

    /**
     * 切换屏幕共享
     */
    @FXML
    private void handleToggleScreenShare() {
        // 切换屏幕共享状态
        isScreenSharing = !isScreenSharing;

        // 更新屏幕共享按钮样式
        btnScreenShare.setStyle(isScreenSharing ?
            "-fx-background-color: #9c27b0; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;" :
            "-fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;");

        if (isScreenSharing) {
            // 关闭摄像头（如果开启）
            if (isCameraOn) {
                stopCamera();
                isCameraOn = false;
                btnCamera.setStyle("-fx-background-color: #c83532; -fx-cursor: hand; -fx-background-radius: 8; -fx-padding: 8 15 8 15;");
            }
            // 禁用摄像头按钮
            btnCamera.setDisable(true);
            btnCamera.setOpacity(0.5);

            startScreenShare();
        } else {
            stopScreenShare();

            // 恢复摄像头按钮
            btnCamera.setDisable(false);
            btnCamera.setOpacity(1.0);
        }

        // 通过WebSocket通知其他参与者屏幕共享状态变化
        sendMediaStatusUpdate();

        logger.info("屏幕共享状态: {}", isScreenSharing ? "开启" : "关闭");
    }

    /**
     * 显示参与者列表
     */
    @FXML
    private void handleShowParticipants() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("参与者列表");
        dialog.setHeaderText("当前会议参与者");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < videoPanels.size(); i++) {
            sb.append((i + 1)).append(". ").append(videoPanels.get(i).getName()).append("\n");
        }

        dialog.setContentText(sb.toString());
        dialog.showAndWait();
    }

    /**
     * 显示设置对话框
     */
    @FXML
    private void handleSettings() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("设置");
        dialog.setHeaderText("会议设置");
        dialog.setContentText("设置功能开发中...\n\n可配置项：\n- 音频设备\n- 视频设备\n- 分辨率\n- 网络设置");
        dialog.showAndWait();
    }

    /**
     * 结束会议
     */
    @FXML
    private void handleEndConference() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("结束会议");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要结束会议吗？");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logger.info("结束会议");

            // 停止媒体
            stopCamera();
            stopScreenShare();

            // 清理 SIP 会议
            if (isRealMode && sipConferenceManager != null) {
                try {
                    sipConferenceManager.leaveConference();
                    logger.info("已离开 SIP 会议");
                } catch (Exception e) {
                    logger.error("离开 SIP 会议失败", e);
                }
            }

            // 清理媒体处理器
            if (mediaHandler != null) {
                try {
                    mediaHandler.cleanup();
                    logger.info("媒体处理器已清理");
                } catch (Exception e) {
                    logger.error("清理媒体处理器失败", e);
                }
            }

            // ========== 断开媒体WebSocket连接 ==========
            if (mediaWebSocketClient != null) {
                try {
                    mediaWebSocketClient.disconnect();
                    logger.info("✅ 媒体WebSocket已断开");
                } catch (Exception e) {
                    logger.error("❌ 断开媒体WebSocket失败", e);
                }
            }

            // 🆕 调用后端API结束会议（设置为不活跃）
            if (currentConference != null && currentConference.getId() != null) {
                endConferenceOnServer(currentConference.getId());
            } else {
                logger.warn("无法结束会议：会议ID为空");
            }

            // 关闭窗口
            closeWindow();
        }
    }

    // ========== 屏幕共享相关 ==========

    /**
     * 启动屏幕共享 - 复用本地视频面板
     */
    private void startScreenShare() {
        try {
            logger.info("启动屏幕共享");

            // 更新本地视频面板标题，表明正在共享屏幕
            if (localVideoPanel != null) {
                localVideoPanel.setName(currentUsername + " (您) - 屏幕共享");
                localVideoPanel.setVideoEnabled(true);
            }

            // ========== 使用 WebSocket 发送屏幕共享帧 ==========
            if (mediaWebSocketClient != null && mediaWebSocketClient.isConnected()) {
                mediaWebSocketClient.startScreenSending(() -> {
                    try {
                        // 捕获屏幕
                        Robot robot = new Robot();
                        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                        BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                        // 更新本地视频面板显示屏幕共享内容
                        if (localVideoPanel != null) {
                            Platform.runLater(() -> {
                                javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(screenCapture, null);
                                localVideoPanel.setImage(fxImage);
                            });
                        }

                        return screenCapture;
                    } catch (Exception e) {
                        logger.error("捕获屏幕失败: {}", e.getMessage());
                        return null;
                    }
                });

                logger.info("✅ 屏幕共享已通过WebSocket启动");
            } else {
                logger.warn("⚠️ 媒体WebSocket未连接，屏幕共享无法启动");
                Platform.runLater(() -> {
                    showAlert("媒体连接未就绪，请稍后再试", Alert.AlertType.WARNING);
                });
            }

        } catch (Exception e) {
            logger.error("屏幕共享启动失败", e);
            Platform.runLater(() -> {
                showAlert("屏幕共享失败！\n\n" + e.getMessage(), Alert.AlertType.ERROR);
            });
        }
    }

    /**
     * 停止屏幕共享
     */
    private void stopScreenShare() {
        logger.info("停止屏幕共享");

        // 停止WebSocket屏幕共享发送
        if (mediaWebSocketClient != null) {
            mediaWebSocketClient.stopScreenSending();
            logger.info("✅ 已停止WebSocket屏幕共享发送");
        }

        // 恢复本地视频面板名称
        if (localVideoPanel != null) {
            localVideoPanel.setName(currentUsername + " (您)");
            localVideoPanel.setVideoEnabled(false); // 关闭视频显示
        }

        // 发送屏幕共享停止消息
        sendScreenShareStop();

        logger.info("屏幕共享已停止");
    }

    /**
     * 处理远程摄像头开始（已禁用 - 仅支持屏幕共享）
     */
    private void handleRemoteCameraStart(String userId, String remoteIp, int remoteCameraPort) {
        logger.info("摄像头功能已禁用，忽略远程摄像头开始消息: userId={}", userId);
        // 摄像头功能已完全移除
    }

    /**
     * 处理远程屏幕共享开始（WebSocket版本 - 不使用RTP）
     */
    private void handleRemoteScreenShareStart(String userId, String remoteIp, Integer remoteVideoPort) {
        try {
            String screenShareId = userId + "_screen";

            // 检查是否已经有屏幕共享面板
            if (remoteScreenSharePanels.containsKey(userId)) {
                logger.warn("用户 {} 的屏幕共享面板已存在", userId);
                return;
            }

            // 获取用户名
            String username = userNameMap.getOrDefault(userId, "用户" + userId);

            // 创建屏幕共享面板
            VideoPanel screenPanel = new VideoPanel(username + " - 屏幕共享", true, false);
            screenPanel.setId(screenShareId);

            // 添加到列表和map
            videoPanels.add(0, screenPanel); // 添加到第一个位置
            remoteScreenSharePanels.put(userId, screenPanel);

            // 更新UI
            Platform.runLater(() -> {
                videoGridPane.getChildren().clear();
                for (int i = 0; i < videoPanels.size(); i++) {
                    addVideoToGrid(videoPanels.get(i));
                }
                updateParticipantCount();
            });

            // 注册远程屏幕共享视频回调（通过WebSocket接收）
            if (mediaHandler != null) {
                mediaHandler.registerRemoteScreenCallback(screenShareId, frame -> {
                    Platform.runLater(() -> screenPanel.setImage(frame));
                });
                logger.info("已注册用户 {} 的屏幕共享视频回调（WebSocket模式）", userId);
            }

            // 显示提示
            appendChatMessage("系统", username + " 开始了屏幕共享", "#9c27b0");

            logger.info("已创建远程屏幕共享面板: userId={} (WebSocket模式)", userId);

        } catch (Exception e) {
            logger.error("处理远程屏幕共享开始失败: userId={}", userId, e);
        }
    }

    /**
     * 处理远程用户的屏幕共享（WebSocket版本）- 复用参与者视频面板
     */
    private void handleRemoteScreenShare(String userId, boolean isSharing) {
        try {
            String screenShareId = userId + "_screen";

            if (isSharing) {
                // 远程用户开始屏幕共享
                logger.info("远程用户 {} 开始屏幕共享", userId);

                // 获取或创建远程参与者的视频面板
                VideoPanel userPanel = remotePanelMap.get(userId);
                if (userPanel == null) {
                    // 如果面板不存在，创建一个
                    String username = userNameMap.getOrDefault(userId, "用户" + userId);
                    userPanel = new VideoPanel(username, false, false);
                    remotePanelMap.put(userId, userPanel);
                    videoPanels.add(userPanel);
                    Platform.runLater(() -> {
                        videoGridPane.getChildren().clear();
                        for (int i = 0; i < videoPanels.size(); i++) {
                            addVideoToGrid(videoPanels.get(i));
                        }
                        updateParticipantCount();
                    });
                }

                // 更新面板名称，表明正在屏幕共享
                String username = userNameMap.getOrDefault(userId, "用户" + userId);
                userPanel.setName(username + " - 屏幕共享");
                userPanel.setVideoEnabled(true);

                // 保存屏幕共享面板的引用
                remoteScreenSharePanels.put(userId, userPanel);

                // 注册远程屏幕共享视频回调（通过WebSocket接收）
                final VideoPanel panel = userPanel;
                if (mediaHandler != null) {
                    mediaHandler.registerRemoteScreenCallback(screenShareId, frame -> {
                        Platform.runLater(() -> panel.setImage(frame));
                    });
                }

                logger.info("已设置远程屏幕共享显示: userId={} (WebSocket模式)", userId);

            } else {
                // 远程用户停止屏幕共享
                logger.info("远程用户 {} 停止屏幕共享", userId);

                // 获取用户面板
                VideoPanel userPanel = remotePanelMap.get(userId);
                if (userPanel != null) {
                    // 恢复面板名称
                    String username = userNameMap.getOrDefault(userId, "用户" + userId);
                    userPanel.setName(username);
                    userPanel.setVideoEnabled(false);

                    // 取消视频回调
                    if (mediaHandler != null) {
                        mediaHandler.unregisterRemoteScreenCallback(screenShareId);
                    }

                    // 从屏幕共享map中移除
                    remoteScreenSharePanels.remove(userId);

                    logger.info("已恢复远程用户面板: userId={}", userId);
                }
            }

        } catch (Exception e) {
            logger.error("处理远程屏幕共享失败: userId={}, isSharing={}", userId, isSharing, e);
        }
    }

    /**
     * 创建远程屏幕共享面板
     */
    private void createRemoteScreenSharePanel(String userId) {
        handleRemoteScreenShare(userId, true);
    }

    /**
     * 移除远程屏幕共享面板
     */
    private void removeRemoteScreenSharePanel(String userId) {
        handleRemoteScreenShare(userId, false);
    }

    // ========== 聊天相关 ==========

    /**
     * 发送聊天消息
     */
    @FXML
    private void handleSendMessage() {
        String message = chatInputField.getText().trim();
        if (!message.isEmpty()) {
            logger.info("发送消息: {}", message);

            // 显示自己的消息
            appendChatMessage(currentUsername, message, "#0096ff");

            // 通过WebSocket发送给其他参与者
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                try {
                    // 从会议URI中提取会议ID
                    String conferenceUri = currentConference.getConferenceUri();
                    String conferenceId;
                    if (conferenceUri.startsWith("sip:")) {
                        conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                    } else {
                        conferenceId = conferenceUri;
                    }

                    // 修复: 移除嵌套的data字段，使用扁平结构匹配服务器期望的MediaFrameMessage格式
                    String json = String.format(
                        "{\"type\":\"conference_message\",\"conferenceId\":%s,\"userId\":\"%s\",\"username\":\"%s\",\"message\":\"%s\"}",
                        conferenceId, currentUserId, currentUsername, message.replace("\"", "\\\"")
                    );
                    webSocketClient.sendJson(json);
                    logger.info("消息已通过WebSocket发送（扁平JSON结构）");
                } catch (Exception e) {
                    logger.error("通过WebSocket发送消息失败", e);
                }
            }

            chatInputField.clear();
        }
    }

    /**
     * 添加聊天消息
     */
    private void appendChatMessage(String sender, String message, String senderColor) {
        Platform.runLater(() -> {
            // 创建消息容器
            HBox messageContainer = new HBox(10);
            messageContainer.setPadding(new Insets(8, 10, 8, 10));
            messageContainer.setStyle("-fx-background-color: #323232; -fx-background-radius: 8;");

            // 时间戳
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            Text timeText = new Text(timeFormat.format(new Date()));
            timeText.setFill(Color.web("#969696"));
            timeText.setFont(Font.font("Microsoft YaHei", 11));

            // 发送者和消息内容
            TextFlow messageFlow = new TextFlow();

            Text senderText = new Text(sender);
            senderText.setFill(Color.web(senderColor));
            senderText.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 13));

            Text colonText = new Text(": ");
            colonText.setFill(Color.web("#c0c0c0"));
            colonText.setFont(Font.font("Microsoft YaHei", 13));

            Text messageText = new Text(message);
            messageText.setFill(Color.WHITE);
            messageText.setFont(Font.font("Microsoft YaHei", 13));

            messageFlow.getChildren().addAll(senderText, colonText, messageText);

            // 添加到容器
            VBox contentBox = new VBox(2);
            Label timeLabel = new Label(timeText.getText());
            timeLabel.setStyle("-fx-text-fill: #969696; -fx-font-size: 11px;");

            contentBox.getChildren().addAll(timeLabel, messageFlow);
            messageContainer.getChildren().add(contentBox);

            chatMessagesBox.getChildren().add(messageContainer);

            // 自动滚动到底部
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    // ========== 工具方法 ==========

    /**
     * 显示提示对话框
     */
    private void showAlert(String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 关闭窗口
     */
    private void closeWindow() {
        Platform.runLater(() -> {
            Stage stage = (Stage) btnEndConference.getScene().getWindow();
            stage.close();
        });
    }

    // ========== Setter 方法 ==========

    /**
     * 清理资源（供外部调用）
     */
    public void cleanup() {
        logger.info("清理会议资源");

        // 停止媒体
        stopCamera();
        stopMicrophone();
        stopScreenShare();

        // 停止所有远程音频播放器
        for (Map.Entry<String, com.sip.client.media.audio.AudioPlayerHelper> entry : audioPlayerMap.entrySet()) {
            try {
                entry.getValue().stop();
                logger.info("已停止用户 {} 的音频播放器", entry.getKey());
            } catch (Exception e) {
                logger.error("停止音频播放器失败: userId={}", entry.getKey(), e);
            }
        }
        audioPlayerMap.clear();

        // 发送离开会议消息并关闭WebSocket
        if (webSocketClient != null) {
            sendLeaveConferenceMessage();
            try {
                webSocketClient.close();
                logger.info("WebSocket已关闭");
            } catch (Exception e) {
                logger.error("关闭WebSocket失败", e);
            }
        }

        // 清理 SIP 会议
        if (isRealMode && sipConferenceManager != null) {
            try {
                sipConferenceManager.leaveConference();
                logger.info("已离开 SIP 会议");
            } catch (Exception e) {
                logger.error("离开 SIP 会议失败", e);
            }
        }

        // 清理媒体处理器
        if (mediaHandler != null) {
            try {
                mediaHandler.cleanup();
                logger.info("媒体处理器已清理");
            } catch (Exception e) {
                logger.error("清理媒体处理器失败", e);
            }
        }

        logger.info("会议资源清理完成");
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
        logger.info("设置当前用户名: {}", username);
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        logger.info("设置当前用户ID (SIP账号): {}", userId);

        // 同时设置mediaHandler的用户ID
        if (mediaHandler != null) {
            mediaHandler.setCurrentUserId(userId);
        }
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        logger.info("设置认证Token: {}", token != null ? "已设置" : "未设置");
    }

    public void setConferenceCode(String code) {
        this.conferenceCode = code;
        logger.info("设置会议号: {}", code);
    }

    public void setConferenceTitle(String title) {
        this.conferenceTitle = title;
        logger.info("设置会议标题: {}", title);
    }

    public void setSipServer(String server) {
        this.sipServer = server;
        logger.info("设置 SIP 服务器: {}", server);
    }

    public void setSipDomain(String domain) {
        this.sipDomain = domain;
        logger.info("设置 SIP 域名: {}", domain);
    }

    public void setRealMode(boolean realMode) {
        this.isRealMode = realMode;
        logger.info("设置会议模式: {}", realMode ? "真实 SIP 模式" : "演示模式");
    }

    public void setAvailableFriends(List<String> friends) {
        this.availableFriends = friends != null ? friends : new ArrayList<>();
        logger.info("设置可邀请好友列表: {}", this.availableFriends);
    }

    // ========== WebSocket 相关方法 ==========

    /**
     * 连接WebSocket
     */
    private void connectWebSocket(ConferenceDTO conference) {
        try {
            // 从会议URI中提取会议ID
            String conferenceUri = conference.getConferenceUri();
            String conferenceId;
            if (conferenceUri.startsWith("sip:")) {
                conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
            } else {
                conferenceId = conferenceUri;
            }

            // ⚡ 修复：使用配置文件中的服务器地址，而不是硬编码localhost
            String wsServerUrl = "ws://" + SipConfig.getHttpServerHost() + ":" + SipConfig.getHttpServerPort() + "/ws/conference?userId=" + currentUserId;
            logger.info("连接WebSocket: {}", wsServerUrl);

            // 创建WebSocket客户端（添加userId参数）
            webSocketClient = new ConferenceWebSocketClient(
                new java.net.URI(wsServerUrl)
            );

            // 设置消息监听器
            webSocketClient.setMessageListener(new ConferenceWebSocketClient.MessageListener() {
                @Override
                public void onMessage(String message) {
                    handleWebSocketMessage(message);
                }

                @Override
                public void onConnected() {
                    logger.info("WebSocket已连接");

                    // 设置WebSocket客户端到mediaHandler
                    if (mediaHandler != null) {
                        mediaHandler.setWebSocketClient(webSocketClient);
                    }

                    // 创建本地视频面板（如果还没有创建）
                    if (localVideoPanel == null) {
                        Platform.runLater(() -> {
                            createLocalVideoPanel();
                            // 不自动启动摄像头，等待用户手动点击
                        });
                    }

                    // 发送加入会议消息
                    sendJoinConferenceMessage(conferenceId);
                }

                @Override
                public void onDisconnected() {
                    logger.info("WebSocket已断开");
                }

                @Override
                public void onError(String error) {
                    logger.error("WebSocket错误: {}", error);
                }
            });

            // 连接WebSocket
            webSocketClient.connect();

            // ========== 初始化媒体WebSocket客户端 ==========
            logger.info("初始化媒体WebSocket客户端: conferenceId={}, userId={}", conferenceId, currentUserId);

            // 修复：currentUserId现在是数字字符串（如"101"），可以安全地转换为Long
            // 之前的错误是尝试解析username（如"user101"），现在已修正
            Long userIdLong = Long.parseLong(currentUserId);

            // ⚡ 修复：使用配置文件中的服务器地址，而不是硬编码localhost
            String mediaWsServerUrl = "ws://" + SipConfig.getHttpServerHost() + ":" + SipConfig.getHttpServerPort();
            logger.info("连接媒体WebSocket: {}", mediaWsServerUrl);

            mediaWebSocketClient = new ConferenceMediaWebSocketClient(
                mediaWsServerUrl,  // WebSocket服务器地址
                Long.parseLong(conferenceId),  // 会议ID
                userIdLong,  // 用户ID
                currentUsername  // 用户名
            );

            // 设置媒体帧接收回调
            mediaWebSocketClient.setOnMediaFrameReceived(frame -> {
                handleReceivedMediaFrame(frame);
            });

            // 连接媒体WebSocket
            mediaWebSocketClient.connect();

            logger.info("✅ 媒体WebSocket客户端已初始化");

        } catch (Exception e) {
            logger.error("连接WebSocket失败", e);
        }
    }

    /**
     * 发送加入会议消息
     */
    private void sendJoinConferenceMessage(String conferenceId) {
        try {
            // 修复: 使用扁平结构,匹配MediaFrameMessage字段，userId需要加引号
            String json = String.format(
                "{\"type\":\"JOIN_CONFERENCE\",\"conferenceId\":%s,\"userId\":\"%s\",\"username\":\"%s\"}",
                conferenceId, currentUserId, currentUsername
            );
            webSocketClient.sendJson(json);
            logger.info("已发送JOIN_CONFERENCE消息: conferenceId={}, userId={}, username={}", conferenceId, currentUserId, currentUsername);
        } catch (Exception e) {
            logger.error("发送加入会议消息失败", e);
        }
    }

    /**
     * 发送离开会议消息
     */
    private void sendLeaveConferenceMessage() {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                // 修复: 使用扁平结构,匹配MediaFrameMessage字段，userId需要加引号
                String json = String.format(
                    "{\"type\":\"LEAVE_CONFERENCE\",\"conferenceId\":%s,\"userId\":\"%s\",\"username\":\"%s\"}",
                    conferenceId, currentUserId, currentUsername
                );
                webSocketClient.sendJson(json);
                logger.info("已发送LEAVE_CONFERENCE消息");
            }
        } catch (Exception e) {
            logger.error("发送离开会议消息失败", e);
        }
    }

    /**
     * 发送媒体状态更新消息（WebSocket版本 - 不携带RTP端口）
     */
    private void sendMediaStatusUpdate() {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                // 发送媒体状态（摄像头功能已禁用）
                String json = String.format(
                    "{\"type\":\"media_status_update\",\"data\":{\"conferenceId\":\"%s\",\"userId\":\"%s\",\"audioEnabled\":%b,\"videoEnabled\":false,\"screenSharing\":%b}}",
                    conferenceId, currentUserId, !isMicMuted, isScreenSharing
                );

                webSocketClient.sendJson(json);
                logger.info("已发送media_status_update消息: audio={}, video=false (已禁用), screen={}",
                           !isMicMuted, isScreenSharing);
            }
        } catch (Exception e) {
            logger.error("发送媒体状态更新消息失败", e);
        }
    }

    /**
     * 发送屏幕共享开始消息（WebSocket版本 - 不携带RTP端口）
     */
    private void sendScreenShareStart() {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null && mediaHandler != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                // 发送屏幕共享开始消息（不携带RTP端口，通过WebSocket传输）
                String json = String.format(
                    "{\"type\":\"screen_share_start\",\"data\":{\"conferenceId\":\"%s\",\"userId\":\"%s\"}}",
                    conferenceId, currentUserId
                );
                webSocketClient.sendJson(json);
                logger.info("已发送screen_share_start消息（WebSocket模式）");
            }
        } catch (Exception e) {
            logger.error("发送屏幕共享开始消息失败", e);
        }
    }

    /**
     * 发送屏幕共享停止消息
     */
    private void sendScreenShareStop() {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                String json = String.format(
                    "{\"type\":\"screen_share_stop\",\"data\":{\"conferenceId\":\"%s\",\"userId\":\"%s\"}}",
                    conferenceId, currentUserId
                );
                webSocketClient.sendJson(json);
                logger.info("已发送screen_share_stop消息");
            }
        } catch (Exception e) {
            logger.error("发送屏幕共享停止消息失败", e);
        }
    }

    /**
     * 发送摄像头接收端口信息
     * 告诉发送方："请把视频发送到我的端口"
     */
    private void sendCameraReceivePortInfo(String targetUserId, int localReceivePort) {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                // 获取本地IP
                com.sip.client.media.MediaManager mediaManager = com.sip.client.media.MediaManager.getInstance();
                String localIp = mediaManager != null && mediaManager.isInitialized() ? mediaManager.getLocalIp() : "";

                String json = String.format(
                    "{\"type\":\"camera_receive_port\",\"data\":{\"conferenceId\":\"%s\",\"userId\":\"%s\",\"targetUserId\":\"%s\",\"receiveIp\":\"%s\",\"receivePort\":%d}}",
                    conferenceId, currentUserId, targetUserId, localIp, localReceivePort
                );
                webSocketClient.sendJson(json);
                logger.info("已发送camera_receive_port消息给用户 {}: {}:{}", targetUserId, localIp, localReceivePort);
            }
        } catch (Exception e) {
            logger.error("发送摄像头接收端口信息失败", e);
        }
    }

    /**
     * 发送屏幕共享接收端口信息
     */
    private void sendScreenShareReceivePortInfo(String targetUserId, int localReceivePort) {
        try {
            if (webSocketClient != null && webSocketClient.isOpen() && currentConference != null) {
                // 从会议URI中提取会议ID
                String conferenceUri = currentConference.getConferenceUri();
                String conferenceId;
                if (conferenceUri.startsWith("sip:")) {
                    conferenceId = conferenceUri.substring(4, conferenceUri.indexOf("@"));
                } else {
                    conferenceId = conferenceUri;
                }

                // 获取本地IP
                com.sip.client.media.MediaManager mediaManager = com.sip.client.media.MediaManager.getInstance();
                String localIp = mediaManager != null && mediaManager.isInitialized() ? mediaManager.getLocalIp() : "";

                String json = String.format(
                    "{\"type\":\"screen_receive_port\",\"data\":{\"conferenceId\":\"%s\",\"userId\":\"%s\",\"targetUserId\":\"%s\",\"receiveIp\":\"%s\",\"receivePort\":%d}}",
                    conferenceId, currentUserId, targetUserId, localIp, localReceivePort
                );
                webSocketClient.sendJson(json);
                logger.info("已发送screen_receive_port消息给用户 {}: {}:{}", targetUserId, localIp, localReceivePort);
            }
        } catch (Exception e) {
            logger.error("发送屏幕共享接收端口信息失败", e);
        }
    }


    /**
     * 处理WebSocket消息
     */
    private void handleWebSocketMessage(String message) {
        try {
            logger.debug("处理WebSocket消息: {}", message);

            // 简单JSON解析 - 提取消息类型
            String messageType = extractJsonValue(message, "type");

            if ("conference_message".equals(messageType)) {
                // 会议聊天消息
                String senderName = extractJsonValue(message, "senderName");
                String messageText = extractJsonValue(message, "message");
                String senderId = extractJsonValue(message, "senderId");

                // 在UI线程中显示消息
                Platform.runLater(() -> {
                    // 判断是否是自己发送的消息（避免重复显示）
                    if (!currentUserId.equals(senderId)) {
                        appendChatMessage(senderName, messageText, "#64b5f6");
                    }
                });

            } else if ("existing_participants".equals(messageType)) {
                // 已有参与者列表（新用户加入时收到）
                logger.info("收到existing_participants消息");

                // 解析参与者列表 - 由于是简单的JSON，我们手动解析
                int participantsStart = message.indexOf("\"participants\":[");
                if (participantsStart != -1) {
                    String participantsStr = message.substring(participantsStart);
                    // 提取所有userId
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"userId\":\"(\\d+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(participantsStr);

                    Platform.runLater(() -> {
                        while (matcher.find()) {
                            String userId = matcher.group(1);
                            if (!currentUserId.equals(userId)) {
                                // 提取对应的username
                                int userIdPos = participantsStr.indexOf("\"userId\":\"" + userId + "\"");
                                int usernamePos = participantsStr.indexOf("\"username\":\"", userIdPos);
                                if (usernamePos != -1) {
                                    // 修复: "username":"的长度是12个字符，不是13
                                    int usernameEnd = participantsStr.indexOf("\"", usernamePos + 12);
                                    String username = participantsStr.substring(usernamePos + 12, usernameEnd);

                                    logger.info("添加已有参与者: userId={}, username={}", userId, username);

                                    ParticipantDTO participant = new ParticipantDTO();
                                    participant.setUserId(Long.parseLong(userId));
                                    participant.setUsername(username);
                                    createRemoteParticipantPanel(participant);
                                }
                            }
                        }
                    });
                }

            } else if ("participant_joined".equals(messageType)) {
                // 参与者加入
                String userId = extractJsonValue(message, "userId");
                String username = extractJsonValue(message, "username");
                String nickname = extractJsonValue(message, "nickname");

                logger.info("收到participant_joined消息: userId={}, username={}, nickname={}", userId, username, nickname);

                // 在UI线程中创建参与者视频面板
                Platform.runLater(() -> {
                    // 避免重复添加自己
                    if (!currentUserId.equals(userId)) {
                        String displayName = nickname != null && !nickname.isEmpty() ? nickname : username;
                        ParticipantDTO participant = new ParticipantDTO();
                        participant.setUserId(Long.parseLong(userId));
                        participant.setUsername(displayName);
                        createRemoteParticipantPanel(participant);

                        // 显示加入提示
                        appendChatMessage("系统", displayName + " 加入了会议", "#4caf50");
                    }
                });

            } else if ("participant_left".equals(messageType)) {
                // 参与者离开
                String userId = extractJsonValue(message, "userId");
                String username = extractJsonValue(message, "username");

                logger.info("收到participant_left消息: userId={}, username={}", userId, username);

                // 在UI线程中移除参与者视频面板
                Platform.runLater(() -> {
                    if (!currentUserId.equals(userId)) {
                        removeParticipantPanel(Long.parseLong(userId));

                        // 显示离开提示
                        appendChatMessage("系统", username + " 离开了会议", "#ff9800");
                    }
                });

            } else if ("participant_status_changed".equals(messageType)) {
                // 参与者状态变化（摄像头、麦克风）- 旧版消息格式
                String userId = extractJsonValue(message, "userId");
                Boolean audioEnabled = extractJsonBoolean(message, "audioEnabled");
                Boolean videoEnabled = extractJsonBoolean(message, "videoEnabled");

                logger.info("收到participant_status_changed消息: userId={}, audio={}, video={}",
                           userId, audioEnabled, videoEnabled);

                // 更新参与者视频面板的状态显示
                if (!currentUserId.equals(userId)) {
                    Platform.runLater(() -> {
                        VideoPanel panel = remotePanelMap.get(userId);
                        if (panel != null && videoEnabled != null) {
                            panel.updateCameraStatus(videoEnabled);
                            logger.info("更新用户 {} 摄像头状态: {}", userId, videoEnabled);
                        }
                    });
                }

            } else if ("media_status_update".equals(messageType)) {
                // 媒体状态更新（新版，携带RTP端口信息）
                String userId = extractJsonValue(message, "userId");
                Boolean audioEnabled = extractJsonBoolean(message, "audioEnabled");
                Boolean videoEnabled = extractJsonBoolean(message, "videoEnabled");
                Boolean screenSharing = extractJsonBoolean(message, "screenSharing");
                String remoteIp = extractJsonValue(message, "remoteIp");
                Integer cameraPort = extractJsonInteger(message, "cameraPort");

                logger.info("收到media_status_update消息: userId={}, audio={}, video={}, screen={}, ip={}, cameraPort={}",
                           userId, audioEnabled, videoEnabled, screenSharing, remoteIp, cameraPort);

                // 避免处理自己的状态更新
                if (!currentUserId.equals(userId)) {
                    Platform.runLater(() -> {
                        // 更新对应参与者的视频面板状态
                        VideoPanel panel = remotePanelMap.get(userId);
                        if (panel != null) {
                            // 更新摄像头状态
                            if (videoEnabled != null) {
                                panel.updateCameraStatus(videoEnabled);
                                logger.info("更新用户 {} 摄像头状态: {}", userId, videoEnabled);

                                // 如果摄像头开启且有RTP端口信息，启动视频接收
                                if (videoEnabled && cameraPort != null && cameraPort > 0 &&
                                    remoteIp != null && !remoteIp.isEmpty()) {
                                    handleRemoteCameraStart(userId, remoteIp, cameraPort);
                                }
                            }

                            // 处理屏幕共享状态
                            if (screenSharing != null) {
                                handleRemoteScreenShare(userId, screenSharing);
                                logger.info("更新用户 {} 屏幕共享状态: {}", userId, screenSharing);
                            }
                        }

                        // 在聊天区域显示状态更新提示
                        StringBuilder statusMsg = new StringBuilder();
                        String username = userNameMap.getOrDefault(userId, "用户" + userId);

                        if (videoEnabled != null) {
                            if (videoEnabled) {
                                statusMsg.append("打开了摄像头");
                            } else {
                                statusMsg.append("关闭了摄像头");
                            }
                        }

                        if (screenSharing != null) {
                            if (statusMsg.length() > 0) statusMsg.append("，");
                            if (screenSharing) {
                                statusMsg.append("开始了屏幕共享");
                            } else {
                                statusMsg.append("停止了屏幕共享");
                            }
                        }

                        if (statusMsg.length() > 0) {
                            appendChatMessage("系统", username + " " + statusMsg.toString(), "#9e9e9e");
                        }
                    });
                }

            } else if ("screen_share_start".equals(messageType)) {
                // 屏幕共享开始
                String userId = extractJsonValue(message, "userId");

                logger.info("收到screen_share_start消息: userId={}", userId);

                // 避免处理自己的屏幕共享
                if (!currentUserId.equals(userId)) {
                    Platform.runLater(() -> {
                        // 创建远程屏幕共享面板
                        createRemoteScreenSharePanel(userId);
                    });
                }

            } else if ("screen_share_stop".equals(messageType)) {
                // 屏幕共享停止
                String userId = extractJsonValue(message, "userId");

                logger.info("收到screen_share_stop消息: userId={}", userId);

                // 避免处理自己的屏幕共享
                if (!currentUserId.equals(userId)) {
                    Platform.runLater(() -> {
                        // 移除远程屏幕共享面板
                        removeRemoteScreenSharePanel(userId);
                    });
                }

            } else if ("camera_receive_port".equals(messageType)) {
                // 摄像头功能已禁用，忽略此消息
                logger.info("收到camera_receive_port消息，但摄像头功能已禁用");

            } else if ("screen_receive_port".equals(messageType)) {
                // 屏幕共享不再使用RTP，忽略此消息
                logger.info("收到screen_receive_port消息，但已改用WebSocket传输");

            } else if ("screen_frame".equals(messageType)) {
                // 屏幕帧数据
                logger.info("收到screen_frame消息，消息长度: {} bytes", message.length());

                String userId = extractJsonValue(message, "userId");
                String frameData = extractJsonValue(message, "frame");
                Integer width = extractJsonInteger(message, "width");
                Integer height = extractJsonInteger(message, "height");

                logger.info("screen_frame解析结果: userId={}, frameData长度={}, width={}, height={}",
                           userId, frameData != null ? frameData.length() : 0, width, height);

                if (!currentUserId.equals(userId) && frameData != null && !frameData.isEmpty()) {
                    // 转发给mediaHandler处理
                    if (mediaHandler != null) {
                        logger.info("准备处理远程屏幕帧: userId={}", userId);
                        mediaHandler.handleRemoteScreenFrame(userId, frameData,
                            width != null ? width : 1920,
                            height != null ? height : 1080);
                    } else {
                        logger.error("mediaHandler为null，无法处理屏幕帧");
                    }
                } else {
                    logger.warn("跳过screen_frame: currentUserId={}, userId={}, frameData为空={}",
                               currentUserId, userId, frameData == null || frameData.isEmpty());
                }

            // ⚡ CRITICAL FIX #10: 修复消息类型大小写不匹配
            // 服务器发送：CONFERENCE_ENDED（大写）
            // 客户端之前监听：conference_ended（小写）→ 永远不匹配！
            } else if ("CONFERENCE_ENDED".equals(messageType)) {
                // 会议结束
                logger.info("收到CONFERENCE_ENDED消息");

                Platform.runLater(() -> {
                    showAlert("会议已由主持人结束", Alert.AlertType.INFORMATION);
                    handleEndConference();
                });
            }

        } catch (Exception e) {
            logger.error("处理WebSocket消息失败: {}", message, e);
        }
    }

    /**
     * 简单的JSON值提取工具（字符串类型）
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return "";
        }
        return json.substring(startIndex, endIndex);
    }

    /**
     * 提取JSON布尔值
     */
    private Boolean extractJsonBoolean(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();

        // 跳过空格
        while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
            startIndex++;
        }

        // 检查是true还是false
        if (json.startsWith("true", startIndex)) {
            return true;
        } else if (json.startsWith("false", startIndex)) {
            return false;
        }

        return null;
    }

    /**
     * 提取JSON整数值
     */
    private Integer extractJsonInteger(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();

        // 跳过空格
        while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
            startIndex++;
        }

        // 提取数字
        int endIndex = startIndex;
        while (endIndex < json.length() && (Character.isDigit(json.charAt(endIndex)) || json.charAt(endIndex) == '-')) {
            endIndex++;
        }

        if (endIndex > startIndex) {
            try {
                return Integer.parseInt(json.substring(startIndex, endIndex));
            } catch (NumberFormatException e) {
                logger.error("解析JSON整数失败: key={}, value={}", key, json.substring(startIndex, endIndex));
                return null;
            }
        }

        return null;
    }

    /**
     * 调用后端API结束会议（设置为不活跃）
     */
    private void endConferenceOnServer(Long conferenceId) {
        // 在新线程中执行，不阻塞UI
        new Thread(() -> {
            try {
                String url = SipConfig.getHttpServerUrl() + "/api/conference/" + conferenceId + "/end";
                logger.info("调用后端API结束会议: conferenceId={}, url={}", conferenceId, url);

                java.net.URL apiUrl = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                // 添加认证Token
                if (authToken != null && !authToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + authToken);
                    logger.debug("请求添加Token");
                }

                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                logger.info("结束会议API响应码: {}", responseCode);

                if (responseCode == 200) {
                    logger.info("✅ 会议已在服务器端标记为结束: conferenceId={}", conferenceId);
                } else {
                    logger.warn("⚠️  结束会议API返回非200状态码: {}", responseCode);
                }

                conn.disconnect();

            } catch (Exception e) {
                logger.error("❌ 调用结束会议API失败: {}", e.getMessage(), e);
            }
        }).start();
    }

    // ========== 内部类：视频面板 ==========

    /**
     * 视频面板类 - JavaFX版本
     */
    private class VideoPanel extends StackPane {
        private String name;
        private boolean cameraOn;
        private boolean isLocal;
        private ImageView imageView;
        private Label nameLabel;
        private StackPane videoContainer;

        public VideoPanel(String name, boolean cameraOn, boolean isLocal) {
            this.name = name;
            this.cameraOn = cameraOn;
            this.isLocal = isLocal;

            // 设置面板样式 - 更柔和的边框和圆角
            setPrefSize(320, 240);
            setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #404040; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);");

            // 创建视频容器
            videoContainer = new StackPane();
            videoContainer.setPrefSize(320, 240);

            // 创建ImageView用于显示视频
            imageView = new ImageView();
            imageView.setFitWidth(320);
            imageView.setFitHeight(240);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            videoContainer.getChildren().add(imageView);

            // 如果摄像头关闭或无视频，显示柔和的渐变背景
            if (!cameraOn || (!isLocal && imageView.getImage() == null)) {
                // 创建渐变背景面板
                Pane gradientPane = new Pane();
                gradientPane.setPrefSize(320, 240);

                if (!cameraOn) {
                    // 摄像头关闭 - 深灰色
                    gradientPane.setStyle("-fx-background-color: #282828;");

                    Label cameraOffLabel = new Label("📷");
                    cameraOffLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: #606060;");
                    StackPane.setAlignment(cameraOffLabel, Pos.CENTER);
                    videoContainer.getChildren().add(cameraOffLabel);
                } else {
                    // 其他参与者 - 柔和蓝色渐变
                    gradientPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e3a5f, #2a5298);");

                    Label avatarLabel = new Label("👤");
                    avatarLabel.setStyle("-fx-font-size: 56px; -fx-text-fill: rgba(255, 255, 255, 0.8);");
                    StackPane.setAlignment(avatarLabel, Pos.CENTER);
                    videoContainer.getChildren().add(avatarLabel);
                }

                videoContainer.getChildren().add(0, gradientPane);
            }

            // 创建名字标签 - 半透明黑色背景
            nameLabel = new Label(name);
            nameLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-text-fill: white; -fx-padding: 6 12 6 12; -fx-background-radius: 6; -fx-font-size: 13px; -fx-font-weight: bold;");
            StackPane.setAlignment(nameLabel, Pos.BOTTOM_LEFT);
            StackPane.setMargin(nameLabel, new Insets(12));

            // 如果是本地视频，添加"本地"标识
            if (isLocal) {
                Label localBadge = new Label("本地");
                localBadge.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-padding: 5 10 5 10; -fx-background-radius: 6; -fx-font-size: 11px; -fx-font-weight: bold;");
                StackPane.setAlignment(localBadge, Pos.TOP_RIGHT);
                StackPane.setMargin(localBadge, new Insets(12));
                getChildren().add(localBadge);
            }

            getChildren().addAll(videoContainer, nameLabel);

            // 添加悬停效果 - 边框变亮
            setOnMouseEntered(e -> setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #0096ff; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,150,255,0.4), 10, 0, 0, 2);"));
            setOnMouseExited(e -> setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #404040; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"));

            // 双击放大
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && imageView.getImage() != null) {
                    showEnlargedView();
                }
            });
        }

        public void setImage(Image image) {
            imageView.setImage(image);
            // 如果设置了图像，移除所有占位符元素（渐变背景和图标）
            if (image != null) {
                // 清除所有子元素，只保留imageView
                videoContainer.getChildren().clear();
                videoContainer.getChildren().add(imageView);
            }
        }

        public String getName() {
            return name;
        }

        /**
         * 设置面板名称
         */
        public void setName(String name) {
            this.name = name;
            if (nameLabel != null) {
                Platform.runLater(() -> nameLabel.setText(name));
            }
        }

        /**
         * 设置视频启用状态
         */
        public void setVideoEnabled(boolean enabled) {
            this.cameraOn = enabled;
            updateCameraStatus(enabled);
        }

        /**
         * 更新摄像头状态
         */
        public void updateCameraStatus(boolean cameraOn) {
            this.cameraOn = cameraOn;
            Platform.runLater(() -> {
                if (!cameraOn) {
                    // 摄像头关闭，显示占位符
                    videoContainer.getChildren().clear();
                    videoContainer.getChildren().add(imageView);

                    // 添加深灰色背景
                    Pane gradientPane = new Pane();
                    gradientPane.setPrefSize(320, 240);
                    gradientPane.setStyle("-fx-background-color: #282828;");
                    videoContainer.getChildren().add(0, gradientPane);

                    // 添加摄像头关闭图标
                    Label cameraOffLabel = new Label("📷");
                    cameraOffLabel.setStyle("-fx-font-size: 48px; -fx-text-fill: #606060;");
                    StackPane.setAlignment(cameraOffLabel, Pos.CENTER);
                    videoContainer.getChildren().add(cameraOffLabel);
                } else {
                    // 摄像头打开，显示蓝色渐变背景（等待视频流）
                    if (imageView.getImage() == null) {
                        videoContainer.getChildren().clear();
                        videoContainer.getChildren().add(imageView);

                        Pane gradientPane = new Pane();
                        gradientPane.setPrefSize(320, 240);
                        gradientPane.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e3a5f, #2a5298);");
                        videoContainer.getChildren().add(0, gradientPane);

                        Label avatarLabel = new Label("👤");
                        avatarLabel.setStyle("-fx-font-size: 56px; -fx-text-fill: rgba(255, 255, 255, 0.8);");
                        StackPane.setAlignment(avatarLabel, Pos.CENTER);
                        videoContainer.getChildren().add(avatarLabel);
                    }
                }
            });
        }

        /**
         * 更新屏幕共享状态
         */
        public void updateScreenShareStatus(boolean isSharing) {
            // 屏幕共享状态更新 - 目前仅记录日志
            // 实际的屏幕共享内容应该通过视频流传输
            logger.debug("用户 {} 屏幕共享状态更新: {}", name, isSharing);
        }

        /**
         * 显示放大视图
         */
        private void showEnlargedView() {
            Stage enlargedStage = new Stage();
            enlargedStage.setTitle(name + " - 放大视图");

            ImageView enlargedImageView = new ImageView(imageView.getImage());
            enlargedImageView.setPreserveRatio(true);
            enlargedImageView.setFitWidth(1200);
            enlargedImageView.setFitHeight(800);

            StackPane root = new StackPane(enlargedImageView);
            root.setStyle("-fx-background-color: black;");

            Scene scene = new Scene(root, 1200, 800);
            enlargedStage.setScene(scene);
            enlargedStage.show();

            // 双击关闭
            enlargedImageView.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    enlargedStage.close();
                }
            });
        }
    }

    // ========== WebSocket媒体帧接收处理 ==========

    /**
     * 处理接收到的远程媒体帧
     */
    private void handleReceivedMediaFrame(com.sip.common.dto.MediaFrameMessage frame) {
        try {
            String senderId = String.valueOf(frame.getUserId());
            String senderName = frame.getUsername();
            String mediaType = frame.getMediaType();

            logger.debug("收到媒体帧: sender={}, type={}, seq={}", senderName, mediaType, frame.getSequence());

            // 根据媒体类型处理
            if ("AUDIO".equals(mediaType)) {
                // 音频帧 - 播放音频
                handleReceivedAudioFrame(senderId, senderName, frame);
            } else {
                // 视频/屏幕帧 - 解码Base64图像数据
                String base64Data = frame.getFrameData();
                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes);
                BufferedImage bufferedImage = javax.imageio.ImageIO.read(bis);

                if (bufferedImage == null) {
                    logger.warn("⚠️ 无法解码媒体帧: sender={}", senderName);
                    return;
                }

                if ("VIDEO".equals(mediaType)) {
                    // 视频帧 - 更新参与者视频面板
                    updateRemoteVideoPanel(senderId, senderName, bufferedImage);
                } else if ("SCREEN".equals(mediaType)) {
                    // 屏幕共享帧 - 更新屏幕共享面板
                    updateRemoteScreenSharePanel(senderId, senderName, bufferedImage);
                }
            }

        } catch (Exception e) {
            logger.error("处理媒体帧失败: {}", e.getMessage());
        }
    }

    /**
     * 处理接收到的音频帧
     */
    private void handleReceivedAudioFrame(String senderId, String senderName, com.sip.common.dto.MediaFrameMessage frame) {
        try {
            // 获取或创建该参与者的音频播放器
            com.sip.client.media.audio.AudioPlayerHelper audioPlayer = audioPlayerMap.get(senderId);

            if (audioPlayer == null) {
                // 首次接收该参与者的音频，创建播放器
                audioPlayer = new com.sip.client.media.audio.AudioPlayerHelper();
                audioPlayer.initialize();
                audioPlayerMap.put(senderId, audioPlayer);
                logger.info("✅ 为用户创建音频播放器: userId={}, username={}", senderId, senderName);
            }

            // Base64解码音频数据
            String base64Data = frame.getFrameData();
            byte[] audioData = java.util.Base64.getDecoder().decode(base64Data);

            // 播放音频数据
            if (audioPlayer.isPlaying()) {
                audioPlayer.playAudioData(audioData, audioData.length);

                // 降低日志频率（每100帧记录一次）
                if (frame.getSequence() % 100 == 0) {
                    logger.trace("播放音频: sender={}, length={} bytes, seq={}",
                        senderName, audioData.length, frame.getSequence());
                }
            }

        } catch (Exception e) {
            logger.error("处理音频帧失败: sender={}, error={}", senderName, e.getMessage());
        }
    }

    /**
     * 更新远程参与者视频面板
     */
    private void updateRemoteVideoPanel(String userId, String username, BufferedImage frame) {
        Platform.runLater(() -> {
            VideoPanel panel = remotePanelMap.get(userId);

            if (panel == null) {
                // 创建新的视频面板
                panel = new VideoPanel(username, false, false);
                remotePanelMap.put(userId, panel);
                videoPanels.add(panel);

                // 重新布局
                videoGridPane.getChildren().clear();
                for (int i = 0; i < videoPanels.size(); i++) {
                    addVideoToGrid(videoPanels.get(i));
                }
                updateParticipantCount();

                logger.info("✅ 创建远程视频面板: userId={}, username={}", userId, username);
            }

            // 更新图像 - 需要转换BufferedImage为JavaFX Image
            javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(frame, null);
            panel.setImage(fxImage);
        });
    }

    /**
     * 更新远程屏幕共享面板
     */
    private void updateRemoteScreenSharePanel(String userId, String username, BufferedImage frame) {
        Platform.runLater(() -> {
            VideoPanel panel = remoteScreenSharePanels.get(userId);

            if (panel == null) {
                // 创建新的屏幕共享面板
                panel = new VideoPanel(username + " - 屏幕共享", true, false);
                remoteScreenSharePanels.put(userId, panel);
                videoPanels.add(0, panel); // 添加到第一个位置

                // 重新布局
                videoGridPane.getChildren().clear();
                for (int i = 0; i < videoPanels.size(); i++) {
                    addVideoToGrid(videoPanels.get(i));
                }
                updateParticipantCount();

                logger.info("✅ 创建远程屏幕共享面板: userId={}, username={}", userId, username);
            }

            // 更新图像 - 需要转换BufferedImage为JavaFX Image
            javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(frame, null);
            panel.setImage(fxImage);
        });
    }
}
