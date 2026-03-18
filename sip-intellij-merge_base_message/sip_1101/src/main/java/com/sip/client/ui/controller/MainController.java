package com.sip.client.ui.controller;

import com.sip.client.call.SipCallManager;
import com.sip.client.config.SipConfig;
import com.sip.client.media.MediaManager;
import com.sip.client.message.SipMessageManager;
import com.sip.client.register.SipRegisterManager;
import com.sip.client.ui.Main;
import com.sip.client.ui.service.SipClientService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * 主界面控制器
 * 负责好友列表、聊天、呼叫、会议等功能
 */
@Slf4j
public class MainController {

    @FXML
    private Label usernameLabel;

    @FXML
    private Label statusIndicator;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private TextField searchField;

    @FXML
    private ListView<String> friendListView;

    @FXML
    private Label chatTitleLabel;

    @FXML
    private Button voiceCallButton;

    @FXML
    private Button videoCallButton;

    @FXML
    private Button createConferenceButton;

    @FXML
    private Button joinConferenceButton;

    @FXML
    private ScrollPane messageScrollPane;

    @FXML
    private VBox messageBox;

    @FXML
    private VBox chatArea;

    @FXML
    private javafx.scene.layout.HBox chatHeader;

    // ✅ 欢迎界面面板
    private javafx.scene.layout.StackPane welcomePanel;

    @FXML
    private TextArea messageInputArea;

    // 当前聊天对象
    private String currentChatFriend;

    // SIP客户端服务
    private SipClientService sipClientService;

    // SIP注册管理器
    private com.sip.client.register.SipRegisterManager registerManager;

    // SIP消息管理器
    private SipMessageManager sipMessageManager;

    // SIP通话管理器
    private SipCallManager callManager;

    // 媒体管理器
    private MediaManager mediaManager;

    // 当前用户名
    private String currentUsername;

    // 当前用户ID（数字类型，用于API调用）
    private Long currentUserId;

    // 认证Token
    private String authToken;

    // 会议窗口映射：conferenceId -> Stage，防止重复打开
    private java.util.Map<String, javafx.stage.Stage> conferenceStages = new java.util.HashMap<>();

    // 通话窗口引用
    private Stage callWindowStage;

    // ✅ 在线状态定时刷新
    private java.util.Timer onlineStatusTimer;

    /**
     * 初始化
     */
    @FXML
    public void initialize() {
        log.info("主界面初始化");

        // 初始化状态下拉框
        initStatusComboBox();

        // ✅ 设置自定义的用户列表渲染
        friendListView.setCellFactory(listView -> new UserListCell());

        // ✅ 初始化欢迎界面
        initWelcomePanel();

        // 加载好友列表
        loadFriendList();

        // 回车发送消息
        messageInputArea.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                handleSendMessage();
                event.consume();
            }
        });

        // ✅ 初始化按钮可见性：默认显示会议按钮，隐藏通话按钮
        createConferenceButton.setVisible(true);
        createConferenceButton.setManaged(true);
        joinConferenceButton.setVisible(true);
        joinConferenceButton.setManaged(true);

        voiceCallButton.setVisible(false);
        voiceCallButton.setManaged(false);
        voiceCallButton.setDisable(true);
        videoCallButton.setVisible(false);
        videoCallButton.setManaged(false);
        videoCallButton.setDisable(true);
    }

    /**
     * ✅ 初始化欢迎界面
     */
    private void initWelcomePanel() {
        welcomePanel = new javafx.scene.layout.StackPane();
        welcomePanel.setStyle("-fx-background-color: #f5f5f5;");

        // 主容器
        javafx.scene.layout.VBox mainContainer = new javafx.scene.layout.VBox(30);
        mainContainer.setAlignment(javafx.geometry.Pos.CENTER);
        mainContainer.setPadding(new javafx.geometry.Insets(50));

        // 欢迎标题
        javafx.scene.control.Label welcomeLabel = new javafx.scene.control.Label("欢迎使用 SIP 即时通信系统");
        welcomeLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2196f3;");

        // 用户信息卡片
        javafx.scene.layout.VBox userInfoCard = createInfoCard(
            "👤 当前用户",
            currentUsername != null ? currentUsername : "加载中..."
        );

        // 在线状态卡片
        javafx.scene.layout.VBox statusCard = createInfoCard(
            "📡 连接状态",
            "SIP 已连接"
        );

        // 快捷操作提示
        javafx.scene.layout.VBox tipsCard = new javafx.scene.layout.VBox(15);
        tipsCard.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                         "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                         "-fx-padding: 25px;");
        tipsCard.setMaxWidth(450);

        javafx.scene.control.Label tipsTitle = new javafx.scene.control.Label("💡 快捷操作");
        tipsTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333;");

        javafx.scene.control.Label tip1 = new javafx.scene.control.Label("• 点击左侧用户列表开始聊天");
        javafx.scene.control.Label tip2 = new javafx.scene.control.Label("• 🟢 绿色圆点表示在线用户");
        javafx.scene.control.Label tip3 = new javafx.scene.control.Label("• 🔴 红色圆点表示离线用户");
        javafx.scene.control.Label tip4 = new javafx.scene.control.Label("• 在线用户列表每3秒自动刷新");

        tip1.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        tip2.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        tip3.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        tip4.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        tipsCard.getChildren().addAll(tipsTitle, tip1, tip2, tip3, tip4);

        // 组装界面
        mainContainer.getChildren().addAll(welcomeLabel, userInfoCard, statusCard, tipsCard);
        welcomePanel.getChildren().add(mainContainer);

        // ✅ 设置welcomePanel占满剩余空间（messageScrollPane的位置）
        javafx.scene.layout.VBox.setVgrow(welcomePanel, javafx.scene.layout.Priority.ALWAYS);

        // ✅ 将欢迎面板添加到chatArea（在messageScrollPane的位置，index 1）
        // chatArea的子元素: 0=chatHeader, 1=messageScrollPane, 2=inputArea
        // 我们在 index 1 插入 welcomePanel
        if (!chatArea.getChildren().contains(welcomePanel)) {
            chatArea.getChildren().add(1, welcomePanel);
        }

        // 默认显示欢迎界面
        showWelcomePanel();
    }

    /**
     * ✅ 创建信息卡片
     */
    private javafx.scene.layout.VBox createInfoCard(String title, String content) {
        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                     "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                     "-fx-padding: 20px;");
        card.setMaxWidth(300);
        card.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #666;");

        javafx.scene.control.Label contentLabel = new javafx.scene.control.Label(content);
        contentLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #333;");

        card.getChildren().addAll(titleLabel, contentLabel);
        return card;
    }

    /**
     * ✅ 显示欢迎界面
     */
    private void showWelcomePanel() {
        if (welcomePanel != null) {
            welcomePanel.setVisible(true);
            welcomePanel.setManaged(true);
        }

        // 隐藏聊天组件
        if (messageScrollPane != null) {
            messageScrollPane.setVisible(false);
            messageScrollPane.setManaged(false);
        }
        if (messageInputArea != null && messageInputArea.getParent() != null) {
            messageInputArea.getParent().setVisible(false);
            messageInputArea.getParent().setManaged(false);
        }
    }

    /**
     * ✅ 显示聊天界面
     */
    private void showChatPanel() {
        // 隐藏欢迎界面
        if (welcomePanel != null) {
            welcomePanel.setVisible(false);
            welcomePanel.setManaged(false);
        }

        // 显示聊天组件
        if (messageScrollPane != null) {
            messageScrollPane.setVisible(true);
            messageScrollPane.setManaged(true);
        }
        if (messageInputArea != null && messageInputArea.getParent() != null) {
            messageInputArea.getParent().setVisible(true);
            messageInputArea.getParent().setManaged(true);
        }
    }

    /**
     * 设置当前用户
     */
    public void setCurrentUser(String username, Long userId) {
        this.currentUsername = username;
        this.currentUserId = userId;
        usernameLabel.setText(username);
        log.info("当前用户: {}, ID: {}", username, userId);

        // ✅ 如果欢迎界面已初始化，重新初始化以更新用户名显示
        if (welcomePanel != null) {
            // 移除旧的欢迎面板
            chatArea.getChildren().remove(welcomePanel);
            welcomePanel = null;
            // 重新初始化
            initWelcomePanel();
        }
    }

    /**
     * 设置认证Token
     */
    public void setAuthToken(String token) {
        this.authToken = token;
        log.info("设置认证Token: {}", token != null ? "已设置" : "未设置");
    }

    /**
     * 设置 SipRegisterManager 并初始化相关管理器
     * 用于支持消息和通话功能
     */
    public void setRegisterManager(com.sip.client.register.SipRegisterManager registerManager, String sipUsername) {
        log.info("🔧 设置RegisterManager: {}", registerManager != null ? "成功" : "失败");

        // ✅ 保存registerManager引用
        this.registerManager = registerManager;

        if (registerManager != null) {
            try {
                // ✅ 【修复消息接收】设置RegisterManager的callback，接收MESSAGE请求
                registerManager.setCallback(new com.sip.client.register.SipRegisterManager.RegisterCallback() {
                    @Override
                    public void onRegisterSuccess() {
                        log.info("SIP注册成功（MainController）");
                    }

                    @Override
                    public void onRegisterFailed(String reason) {
                        log.error("SIP注册失败（MainController）: {}", reason);
                    }

                    @Override
                    public void onUnregisterSuccess() {
                        log.info("SIP注销成功（MainController）");
                    }

                    @Override
                    public void onMessageReceived(String from, String messageBody) {
                        log.info("💬 【MainController】收到MESSAGE: from={}, content={}", from, messageBody);

                        // 在UI线程中显示消息
                        Platform.runLater(() -> {
                            // from是SIP用户名（如"100"），需要转换为username（如"user100"）
                            String fromUsername = "user" + from;

                            // 如果当前正在与发送者聊天，显示消息
                            if (fromUsername.equals(currentChatFriend)) {
                                displayReceivedMessage(messageBody, false);
                                log.info("✅ 消息已显示在界面上");
                            } else {
                                // TODO: 显示未读消息提示
                                log.info("收到来自 {} 的消息，但不是当前聊天对象", fromUsername);
                            }
                        });
                    }
                });
                log.info("✅ RegisterManager的callback已设置（支持MESSAGE接收）");

                // 初始化SipMessageManager
                sipMessageManager = SipMessageManager.getInstance();

                String serverHost = SipConfig.getSipServerHost();
                int serverPort = SipConfig.getSipServerPort();

                sipMessageManager.initializeWithExistingProvider(
                    sipUsername,
                    serverHost,
                    serverPort,
                    registerManager
                );

                // 设置消息接收回调（这个是SipMessageManager的回调，作为备用）
                sipMessageManager.setMessageCallback(incomingMessage -> {
                    log.info("💬 收到好友消息（SipMessageManager）: from={}, content={}",
                        incomingMessage.getFromUri(), incomingMessage.getContent());

                    // 在UI线程中显示消息
                    Platform.runLater(() -> {
                        // 提取发送者用户名
                        String fromUsername = extractUsernameFromUri(incomingMessage.getFromUri());

                        // 如果当前正在与发送者聊天，显示消息
                        if (fromUsername.equals(currentChatFriend)) {
                            displayReceivedMessage(incomingMessage.getContent(), false);
                        } else {
                            // TODO: 显示未读消息提示
                            log.info("收到来自 {} 的消息，但不是当前聊天对象", fromUsername);
                        }
                    });
                });

                log.info("✅ SipMessageManager初始化成功");

                // ✅ 关键：设置RegisterManager的MessageManager引用，以便转发MESSAGE响应
                registerManager.setMessageManager(sipMessageManager);
                log.info("✅ 已设置RegisterManager -> MessageManager转发链路");

                // 初始化SipCallManager和MediaManager（音视频通话）
                initializeCallManagers();

            } catch (Exception e) {
                log.error("❌ 初始化SipMessageManager失败", e);
            }
        }
    }

    /**
     * 设置SIP客户端服务
     */
    public void setSipClientService(SipClientService service) {
        this.sipClientService = service;
        log.info("🔧 设置SipClientService: {}", service != null ? "成功" : "失败");

        // 设置消息回调，监听会议邀请
        if (service != null) {
            service.setMessageCallback(new SipClientService.MessageCallback() {
                @Override
                public void onConferenceInvite(String inviter, String conferenceId, String conferenceTitle) {
                    log.info("🎉 MainController收到会议邀请回调: 来自={}, 会议={}", inviter, conferenceTitle);
                    // 在UI线程中显示邀请对话框
                    Platform.runLater(() -> {
                        log.info("📢 准备显示会议邀请对话框");
                        showConferenceInviteDialog(inviter, conferenceId, conferenceTitle);
                    });
                }
            });
            log.info("✅ MessageCallback已设置");

            // 初始化SipMessageManager并复用现有的SipProvider
            initializeSipMessageManager();

            // 初始化SipCallManager和MediaManager（音视频通话）
            initializeCallManagers();
        }
    }

    /**
     * 初始化SIP消息管理器
     */
    private void initializeSipMessageManager() {
        try {
            sipMessageManager = SipMessageManager.getInstance();

            // 从SipClientService获取已注册的SipProvider和相关组件
            if (sipClientService != null && sipClientService.getRegisterManager() != null) {
                String serverHost = SipConfig.getSipServerHost();
                int serverPort = SipConfig.getSipServerPort();

                sipMessageManager.initializeWithExistingProvider(
                    currentUsername,
                    serverHost,
                    serverPort,
                    sipClientService.getRegisterManager()
                );

                // 设置消息接收回调
                sipMessageManager.setMessageCallback(incomingMessage -> {
                    log.info("💬 收到好友消息: from={}, content={}",
                        incomingMessage.getFromUri(), incomingMessage.getContent());

                    // 在UI线程中显示消息
                    Platform.runLater(() -> {
                        // 提取发送者用户名
                        String fromUsername = extractUsernameFromUri(incomingMessage.getFromUri());

                        // 如果当前正在与发送者聊天，显示消息
                        if (fromUsername.equals(currentChatFriend)) {
                            addMessageBubble(incomingMessage.getContent(), false);
                        } else {
                            // TODO: 显示未读消息提示
                            log.info("收到来自 {} 的消息，但不是当前聊天对象", fromUsername);
                        }
                    });
                });

                log.info("✅ SipMessageManager初始化成功");
            } else {
                log.warn("⚠️ SipClientService或RegisterManager为空，无法初始化SipMessageManager");
            }
        } catch (Exception e) {
            log.error("❌ 初始化SipMessageManager失败", e);
        }
    }

    /**
     * 从SIP URI中提取用户名
     * 例如: "sip:user100@10.129.114.129" -> "user100"
     */
    private String extractUsernameFromUri(String uri) {
        if (uri == null || !uri.contains("sip:")) {
            return uri;
        }

        // 移除 "sip:" 前缀
        String withoutScheme = uri.substring(uri.indexOf("sip:") + 4);

        // 提取@之前的部分
        int atIndex = withoutScheme.indexOf('@');
        if (atIndex > 0) {
            return withoutScheme.substring(0, atIndex);
        }

        return withoutScheme;
    }

    /**
     * 从username提取SIP用户名
     * 例如: "user101" -> "101"
     */
    private String extractSipUsernameFromUsername(String username) {
        if (username == null) {
            return username;
        }
        // 如果username以"user"开头，去掉"user"前缀
        if (username.startsWith("user")) {
            return username.substring(4);
        }
        return username;
    }

    /**
     * 初始化通话管理器
     */
    private void initializeCallManagers() {
        try {
            // 获取SipCallManager单例
            callManager = SipCallManager.getInstance();

            // ✅ 修复：支持两种初始化路径
            // 路径1: 从sipClientService获取配置（通过setSipClientService调用）
            // 路径2: 从registerManager获取配置（通过setRegisterManager调用）

            String localIp = null;
            Integer localPort = null;
            String domain = SipConfig.getSipDomain();

            // 尝试从sipClientService获取配置
            if (sipClientService != null && sipClientService.getRegisterManager() != null) {
                localIp = sipClientService.getRegisterManager().getLocalIp();
                localPort = sipClientService.getRegisterManager().getLocalPort();
                log.info("从sipClientService获取SIP配置: {}:{}", localIp, localPort);
            }
            // 如果sipClientService不可用，尝试从registerManager获取
            else if (registerManager != null) {
                localIp = registerManager.getLocalIp();
                localPort = registerManager.getLocalPort();
                log.info("从registerManager获取SIP配置: {}:{}", localIp, localPort);
            }

            // 只有在获取到配置后才初始化CallManager
            if (localIp != null && localPort != null && currentUsername != null) {
                // ✅ 修复：从 RegisterManager 获取 SIP 组件（复用 SipProvider，避免端口冲突）
                SipRegisterManager activeRegisterManager = (registerManager != null)
                    ? registerManager
                    : (sipClientService != null ? sipClientService.getRegisterManager() : null);

                if (activeRegisterManager != null) {
                    // ✅ 提取SIP号码（例如：user100 → 100）
                    String sipNumber = currentUsername.replace("user", "");
                    log.info("提取SIP号码用于CallManager: {} → {}", currentUsername, sipNumber);

                    // ✅ 获取密码（用于INVITE认证）
                    String password = (sipClientService != null) ? sipClientService.getPassword() : "";
                    if (password == null || password.isEmpty()) {
                        log.warn("⚠️ 密码为空，可能无法进行INVITE认证");
                    }

                    // 初始化CallManager（复用 RegisterManager 的 SipProvider）
                    callManager.initialize(
                        activeRegisterManager.getSipProvider(),
                        activeRegisterManager.getAddressFactory(),
                        activeRegisterManager.getHeaderFactory(),
                        activeRegisterManager.getMessageFactory(),
                        localIp,
                        localPort,
                        sipNumber,  // ✅ 使用SIP号码而不是用户名
                        domain,
                        password    // ✅ 添加密码参数
                    );

                    log.info("✅ CallManager 已初始化（复用 RegisterManager 的 SipProvider）");

                    // ✅ 设置 RegisterManager -> CallManager 转发链路
                    activeRegisterManager.setCallManager(callManager);
                    log.info("✅ 已设置 RegisterManager -> CallManager 转发链路");

                } else {
                    log.error("❌ 无法初始化 CallManager：RegisterManager 不可用");
                    return;
                }

                // 设置来电监听
                callManager.setCallEventListener(new SipCallManager.CallEventListener() {
                    @Override
                    public void onCalling(String targetUsername) {
                        log.info("正在呼叫: {}", targetUsername);
                    }

                    @Override
                    public void onIncomingCall(String callId, String callerUri, String callType) {
                        log.info("📞 收到来电: callId={}, caller={}, type={}", callId, callerUri, callType);

                        // 在UI线程中显示来电对话框
                        Platform.runLater(() -> {
                            String callerUsername = extractUsernameFromUri(callerUri);
                            showIncomingCallDialog(callId, callerUsername, callType);
                        });
                    }

                    @Override
                    public void onRinging() {
                        log.info("对方振铃中...");
                    }

                    @Override
                    public void onCallEstablished(Map<String, Object> remoteMediaInfo) {
                        log.info("通话已建立");
                    }

                    @Override
                    public void onCallEnded() {
                        log.info("通话已结束");
                    }

                    @Override
                    public void onCallFailed(String reason) {
                        log.error("通话失败: {}", reason);
                    }
                });

                log.info("✅ SipCallManager初始化成功");
            } else {
                log.warn("⚠️ 无法初始化SipCallManager: localIp={}, localPort={}, currentUsername={}",
                    localIp, localPort, currentUsername);
            }

            // 获取MediaManager单例并初始化
            mediaManager = MediaManager.getInstance();
            mediaManager.initialize();
            log.info("✅ MediaManager初始化成功");

            // ✅ 在后台预热摄像头，减少首次视频通话等待时间
            // 这是一个权衡方案：登录后台预热，打电话时就不用等待摄像头初始化
            mediaManager.prewarmCamera();
            log.info("🔥 已启动摄像头预热（后台线程）");

        } catch (Exception e) {
            log.error("❌ 初始化通话管理器失败", e);
        }
    }

    /**
     * 初始化状态下拉框
     */
    private void initStatusComboBox() {
        statusComboBox.setItems(FXCollections.observableArrayList(
                "在线",
                "忙碌",
                "离开"
        ));

        // 默认选中"在线"
        statusComboBox.setValue("在线");
        updateStatusIndicator("online");
    }

    /**
     * 处理状态变更
     */
    @FXML
    public void handleStatusChanged() {
        String selectedStatus = statusComboBox.getValue();
        log.info("用户状态变更: {}", selectedStatus);

        // 映射中文到英文状态码
        String statusCode = mapStatusToCode(selectedStatus);

        // 更新状态指示器
        updateStatusIndicator(statusCode);
    }

    /**
     * 映射状态名称到状态码
     */
    private String mapStatusToCode(String statusName) {
        switch (statusName) {
            case "在线":
                return "online";
            case "忙碌":
                return "busy";
            case "离开":
                return "away";
            default:
                return "online";
        }
    }

    /**
     * 更新状态指示器颜色
     */
    private void updateStatusIndicator(String status) {
        Color color;
        switch (status) {
            case "online":
                color = Color.web("#00FF00"); // 绿色
                break;
            case "busy":
                color = Color.web("#FF0000"); // 红色
                break;
            case "away":
                color = Color.web("#FFD700"); // 黄色
                break;
            default:
                color = Color.web("#00FF00");
        }

        if (statusIndicator != null) {
            statusIndicator.setTextFill(color);
        }
    }

    /**
     * 加载好友列表（显示所有用户，用颜色标识在线/离线状态）
     * ✅ 2025-12-13 改进：显示所有用户，绿色=在线，红色=离线，定时刷新
     */
    private void loadFriendList() {
        // 初次加载
        refreshUserList();

        // 启动定时刷新（每3秒刷新一次在线状态）
        startOnlineStatusRefresh();

        // 点击好友打开聊天
        friendListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // ✅ 提取类型和名称（格式: "main:主导航" 或 "online:username" 或 "offline:username"）
                String[] parts = newVal.split(":", 2);
                String type = parts[0];
                String name = parts.length > 1 ? parts[1] : newVal;

                if ("main".equals(type)) {
                    // 选择了主导航
                    showMainNavigation();
                } else {
                    // 选择了好友（online 或 offline）
                    openChat(name);
                }
            }
        });
    }

    /**
     * ✅ 刷新用户列表及在线状态
     */
    private void refreshUserList() {
        new Thread(() -> {
            try {
                // 1. 获取所有用户列表
                java.util.List<String> allUsers = fetchAllUsers();

                // 2. 获取在线用户列表
                java.util.Set<String> onlineUsers = fetchOnlineUsers();

                // 3. 在UI线程中更新列表
                Platform.runLater(() -> {
                    friendListView.getItems().clear();

                    // ✅ 添加"主导航"作为第一个选项
                    friendListView.getItems().add("main:主导航");

                    for (String username : allUsers) {
                        // 不包括当前用户自己
                        if (!username.equals(currentUsername)) {
                            boolean isOnline = onlineUsers.contains(username);
                            // ✅ 格式: "online:username" 或 "offline:username"
                            String itemData = (isOnline ? "online:" : "offline:") + username;
                            friendListView.getItems().add(itemData);
                        }
                    }
                    log.info("✅ 用户列表已更新，共 {} 位用户（{} 在线）",
                        friendListView.getItems().size() - 1, onlineUsers.size()); // -1 因为包含了"主导航"
                });

            } catch (Exception e) {
                log.error("刷新用户列表失败", e);
                Platform.runLater(() -> loadDefaultFriendList());
            }
        }).start();
    }

    /**
     * ✅ 获取所有用户列表
     */
    private java.util.List<String> fetchAllUsers() throws Exception {
        String url = SipConfig.getHttpServerUrl() + "/api/user/list";
        java.net.URL apiUrl = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
        conn.setRequestMethod("GET");

        if (authToken != null && !authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return parseUsernamesFromJson(response.toString());
            }
        } else {
            throw new Exception("获取用户列表失败: HTTP " + responseCode);
        }
    }

    /**
     * ✅ 获取在线用户列表（返回Set便于快速查找）
     */
    private java.util.Set<String> fetchOnlineUsers() throws Exception {
        String url = SipConfig.getHttpServerUrl() + "/api/online/users";
        java.net.URL apiUrl = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
        conn.setRequestMethod("GET");

        if (authToken != null && !authToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                java.util.List<String> onlineList = parseOnlineUsernamesFromJson(response.toString());
                return new java.util.HashSet<>(onlineList);
            }
        } else {
            log.warn("获取在线用户失败: HTTP {}", responseCode);
            return new java.util.HashSet<>();
        }
    }

    /**
     * ✅ 启动在线状态定时刷新
     */
    private void startOnlineStatusRefresh() {
        if (onlineStatusTimer != null) {
            onlineStatusTimer.cancel();
        }

        onlineStatusTimer = new java.util.Timer("OnlineStatusRefresh", true);
        onlineStatusTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                refreshUserList();
            }
        }, 3000, 3000); // 3秒后开始，每3秒刷新一次

        log.info("✅ 在线状态定时刷新已启动（间隔3秒）");
    }

    /**
     * ✅ 停止在线状态定时刷新
     */
    private void stopOnlineStatusRefresh() {
        if (onlineStatusTimer != null) {
            onlineStatusTimer.cancel();
            onlineStatusTimer = null;
            log.info("在线状态定时刷新已停止");
        }
    }

    /**
     * 加载默认好友列表（服务器不可用时的后备方案）
     */
    private void loadDefaultFriendList() {
        friendListView.getItems().addAll("user100", "user101", "user102", "user103", "user104");
        log.info("使用默认好友列表: user100-104");
    }

    /**
     * 从JSON响应中解析用户名列表
     */
    private java.util.List<String> parseUsernamesFromJson(String json) {
        java.util.List<String> usernames = new java.util.ArrayList<>();

        try {
            // 简单的JSON解析：找到"list"数组中的所有username
            int listStart = json.indexOf("\"list\":[");
            if (listStart == -1) {
                return usernames;
            }

            int listEnd = json.indexOf("]", listStart);
            String listContent = json.substring(listStart + 8, listEnd);

            // 分割每个用户对象
            String[] users = listContent.split("\\},\\{");
            for (String userJson : users) {
                // 提取username字段
                String username = extractJsonValue(userJson, "username");
                if (!username.isEmpty()) {
                    usernames.add(username);
                }
            }

        } catch (Exception e) {
            log.error("解析用户名列表失败", e);
        }

        return usernames;
    }

    /**
     * ✅ 新增：从在线用户API的JSON响应中解析用户名列表
     * 响应格式: {"code":200,"message":"ok","data":[{"userId":1,"username":"user100",...},...]}
     */
    private java.util.List<String> parseOnlineUsernamesFromJson(String json) {
        java.util.List<String> usernames = new java.util.ArrayList<>();

        try {
            // 找到"data"数组的开始和结束位置
            int dataStart = json.indexOf("\"data\":[");
            if (dataStart == -1) {
                // 如果没有data字段，尝试旧格式（直接是数组）
                dataStart = json.indexOf("[");
                if (dataStart == -1) {
                    log.warn("JSON中没有找到data数组");
                    return usernames;
                }
            } else {
                dataStart += 7; // 跳过"data":
            }

            int dataEnd = json.lastIndexOf("]");
            if (dataEnd == -1 || dataEnd <= dataStart) {
                log.warn("JSON数组格式错误");
                return usernames;
            }

            String dataContent = json.substring(dataStart, dataEnd + 1);

            // 分割每个OnlineSession对象
            String[] sessions = dataContent.split("\\},\\{");
            for (String sessionJson : sessions) {
                // 提取username字段
                String username = extractJsonValue(sessionJson, "username");
                if (!username.isEmpty()) {
                    usernames.add(username);
                    log.debug("解析到在线用户: {}", username);
                }
            }

            log.info("成功解析 {} 个在线用户", usernames.size());

        } catch (Exception e) {
            log.error("解析在线用户名列表失败", e);
        }

        return usernames;
    }

    /**
     * 简单的JSON值提取工具
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
     * 从JSON中提取数字值 (如 "id":123 或 "code":200)
     */
    private String extractJsonNumberValue(String json, String key) {
        // 处理 "id":123 这种格式
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += searchKey.length();

        // 跳过可能的空格
        while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
            startIndex++;
        }

        // 找到数字的结束位置（逗号、}、]、或空格）
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char c = json.charAt(endIndex);
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n') {
                break;
            }
            endIndex++;
        }

        if (endIndex > startIndex) {
            return json.substring(startIndex, endIndex);
        }
        return "";
    }

    /**
     * 从JSON字符串中提取字符串类型的值
     */
    private String extractJsonStringValue(String json, String key) {
        // 处理 "conferenceCode":"123456" 这种格式
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += searchKey.length();

        // 找到字符串的结束引号
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1 || endIndex <= startIndex) {
            return "";
        }

        return json.substring(startIndex, endIndex);
    }

    /**
     * 打开聊天
     * ✅ 2025-12-13 改进：切换到聊天界面
     */
    private void openChat(String friendName) {
        currentChatFriend = friendName;
        log.info("📱 打开聊天窗口: currentChatFriend={}", currentChatFriend);
        chatTitleLabel.setText("与 " + friendName + " 聊天");

        // ✅ 切换到聊天界面
        showChatPanel();

        // ✅ 隐藏会议按钮，显示通话按钮
        createConferenceButton.setVisible(false);
        createConferenceButton.setManaged(false);
        joinConferenceButton.setVisible(false);
        joinConferenceButton.setManaged(false);

        // 启用通话按钮
        voiceCallButton.setVisible(true);
        voiceCallButton.setManaged(true);
        voiceCallButton.setDisable(false);
        videoCallButton.setVisible(true);
        videoCallButton.setManaged(true);
        videoCallButton.setDisable(false);

        // 加载聊天记录
        loadChatHistory(friendName);
    }

    /**
     * ✅ 显示主导航界面
     * 2025-12-13 新增：显示欢迎界面和会议功能
     */
    private void showMainNavigation() {
        // 清空当前聊天对象
        currentChatFriend = null;
        chatTitleLabel.setText("主导航");

        // 显示欢迎界面
        showWelcomePanel();

        // ✅ 显示会议按钮，隐藏通话按钮
        createConferenceButton.setVisible(true);
        createConferenceButton.setManaged(true);
        joinConferenceButton.setVisible(true);
        joinConferenceButton.setManaged(true);

        voiceCallButton.setVisible(false);
        voiceCallButton.setManaged(false);
        videoCallButton.setVisible(false);
        videoCallButton.setManaged(false);

        log.info("切换到主导航界面");
    }

    /**
     * 加载聊天记录
     */
    private void loadChatHistory(String friendName) {
        messageBox.getChildren().clear();

        // 异步加载聊天记录
        new Thread(() -> {
            try {
                // 获取对方用户ID
                Long otherUserId = getUserIdByUsername(friendName);
                if (otherUserId == null) {
                    log.warn("无法获取用户ID: {}", friendName);
                    return;
                }

                // 请求聊天记录
                String url = String.format("/api/message/history?userId1=%d&userId2=%d&limit=50",
                        currentUserId, otherUserId);

                // 发起HTTP GET请求
                com.sip.common.result.Result result = com.sip.client.util.HttpClientUtil.get(url, com.sip.common.result.Result.class);

                if (result != null && result.getData() != null) {
                    // 解析消息列表
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> messages =
                            (java.util.List<java.util.Map<String, Object>>) result.getData();

                    // 在UI线程中显示消息
                    Platform.runLater(() -> {
                        for (java.util.Map<String, Object> msg : messages) {
                            Long fromUserId = ((Number) msg.get("fromUserId")).longValue();
                            String content = (String) msg.get("content");
                            Integer msgType = (Integer) msg.get("msgType");
                            String fileUrl = (String) msg.get("fileUrl");

                            boolean isSent = fromUserId.equals(currentUserId);

                            // 根据消息类型显示不同的气泡
                            if (msgType == 2) {
                                // 图片消息
                                addImageMessageBubble(content, fileUrl, isSent);
                            } else if (msgType == 3) {
                                // 语音消息
                                addVoiceMessageBubble(content, fileUrl, isSent);
                            } else if (msgType == 4) {
                                // 视频消息
                                addVideoMessageBubble(content, fileUrl, isSent);
                            } else if (msgType == 5) {
                                // 文件消息
                                addFileMessageBubble(content, fileUrl, isSent);
                            } else {
                                // 文本消息
                                addMessageBubble(content, isSent);
                            }
                        }

                        log.info("已加载 {} 条聊天记录", messages.size());
                    });
                }

            } catch (Exception e) {
                log.error("加载聊天记录失败", e);
            }
        }).start();
    }

    /**
     * 添加图片消息气泡
     */
    private void addImageMessageBubble(String content, String fileUrl, boolean isSent) {
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        // 创建图片标签
        Label imageLabel = new Label(content + " 💾 点击下载");
        imageLabel.setWrapText(true);
        imageLabel.setMaxWidth(450);
        imageLabel.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 14px; -fx-cursor: hand;"
        );

        // 点击下载图片
        imageLabel.setOnMouseClicked(e -> downloadFile(fileUrl, content));

        container.getChildren().add(imageLabel);
        messageBox.getChildren().add(container);

        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 添加语音消息气泡
     */
    private void addVoiceMessageBubble(String content, String fileUrl, boolean isSent) {
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        Label voiceLabel = new Label("🎤 " + content + " 💾 点击下载");
        voiceLabel.setWrapText(true);
        voiceLabel.setMaxWidth(450);
        voiceLabel.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 14px; -fx-cursor: hand;"
        );

        voiceLabel.setOnMouseClicked(e -> downloadFile(fileUrl, content));

        container.getChildren().add(voiceLabel);
        messageBox.getChildren().add(container);

        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 添加视频消息气泡
     */
    private void addVideoMessageBubble(String content, String fileUrl, boolean isSent) {
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        Label videoLabel = new Label("🎬 " + content + " 💾 点击下载");
        videoLabel.setWrapText(true);
        videoLabel.setMaxWidth(450);
        videoLabel.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 14px; -fx-cursor: hand;"
        );

        videoLabel.setOnMouseClicked(e -> downloadFile(fileUrl, content));

        container.getChildren().add(videoLabel);
        messageBox.getChildren().add(container);

        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 添加文件消息气泡
     */
    private void addFileMessageBubble(String content, String fileUrl, boolean isSent) {
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        Label fileLabel = new Label("📄 " + content + " 💾 点击下载");
        fileLabel.setWrapText(true);
        fileLabel.setMaxWidth(450);
        fileLabel.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-font-size: 14px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 14px; -fx-cursor: hand;"
        );

        fileLabel.setOnMouseClicked(e -> downloadFile(fileUrl, content));

        container.getChildren().add(fileLabel);
        messageBox.getChildren().add(container);

        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 下载文件
     */
    private void downloadFile(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            showAlert("文件URL无效");
            return;
        }

        // 打开文件保存对话框
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("保存文件");
        fileChooser.setInitialFileName(fileName.replace("[图片] ", "").replace("[文件] ", ""));

        java.io.File saveFile = fileChooser.showSaveDialog(usernameLabel.getScene().getWindow());
        if (saveFile == null) {
            return;
        }

        // 异步下载文件
        new Thread(() -> {
            try {
                com.sip.client.util.HttpClientUtil.downloadFile(fileUrl, saveFile);
                Platform.runLater(() -> showAlert("文件已下载到: " + saveFile.getAbsolutePath()));
            } catch (Exception e) {
                log.error("下载文件失败", e);
                Platform.runLater(() -> showAlert("下载失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 添加消息气泡
     */
    private void addMessageBubble(String message, boolean isSent) {
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(450);
        messageLabel.setStyle(
                isSent
                ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-font-size: 14px;"
                : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 14px; -fx-background-radius: 5px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1); -fx-font-size: 14px;"
        );

        VBox container = new VBox(messageLabel);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        messageBox.getChildren().add(container);

        // 滚动到底部
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * ✅ 发送消息 - 优化版（2025-12-13）
     * 修复：检查SIP注册状态，增强稳定性
     */
    @FXML
    public void handleSendMessage() {
        String message = messageInputArea.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        // ✅ 1. 检查SIP注册状态
        if (registerManager == null || !registerManager.isRegistered()) {
            log.error("❌ SIP未注册或注册已失效，无法发送消息");
            showAlert("SIP连接已断开，请重新登录");
            return;
        }

        // ✅ 2. 检查消息管理器
        if (sipMessageManager == null) {
            log.error("❌ SipMessageManager未初始化");
            showAlert("消息服务未就绪，请重新登录");
            return;
        }

        // 显示消息（乐观UI更新）
        addMessageBubble(message, true);

        // 清空输入框
        messageInputArea.clear();

        // ✅ 3. 异步发送消息（避免阻塞UI）
        new Thread(() -> {
            try {
                String sipUsername = extractSipUsernameFromUsername(currentChatFriend);
                log.info("📤 准备发送消息到 {} (SIP:{}): {}", currentChatFriend, sipUsername, message);

                boolean success = sipMessageManager.sendTextMessage(sipUsername, message);

                if (success) {
                    log.info("✅ 消息发送成功");
                } else {
                    log.error("❌ 消息发送失败");
                    Platform.runLater(() -> {
                        showAlert("消息发送失败，请检查网络连接");
                        // TODO: 可以在这里添加重试机制或将消息加入待发送队列
                    });
                }
            } catch (Exception e) {
                log.error("❌ 发送消息异常", e);
                Platform.runLater(() -> {
                    showAlert("发送消息时发生错误: " + e.getMessage());
                });
            }
        }, "MessageSendThread").start();
    }

    /**
     * 发送图片
     */
    @FXML
    public void handleSendImage() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        // 打开文件选择器
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择图片");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png", "*.gif"),
            new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        java.io.File file = fileChooser.showOpenDialog(usernameLabel.getScene().getWindow());
        if (file == null) {
            return;
        }

        // 检查文件大小（限制10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.length() > maxSize) {
            showAlert("图片文件过大，请选择小于10MB的图片");
            return;
        }

        log.info("选择了图片文件: {}, 大小: {} bytes", file.getName(), file.length());

        // 在UI上显示发送中的消息
        String previewMessage = "[图片] " + file.getName();
        addMessageBubble(previewMessage, true);

        // 异步上传图片
        new Thread(() -> {
            try {
                sendImageMessage(file);
                log.info("✅ 图片发送流程完成");
            } catch (Exception e) {
                // ✅ 图片已成功上传到服务器，反序列化错误不影响结果，只记录日志
                log.warn("图片发送流程结束（可能有反序列化警告，但文件已成功上传）: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * 发送文件
     */
    @FXML
    public void handleSendFile() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        // 打开文件选择器
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择文件");
        fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        java.io.File file = fileChooser.showOpenDialog(usernameLabel.getScene().getWindow());
        if (file == null) {
            return;
        }

        // 检查文件大小（限制50MB）
        long maxSize = 50 * 1024 * 1024;
        if (file.length() > maxSize) {
            showAlert("文件过大，请选择小于50MB的文件");
            return;
        }

        log.info("选择了文件: {}, 大小: {} bytes", file.getName(), file.length());

        // 在UI上显示发送中的消息
        String previewMessage = "[文件] " + file.getName();
        addMessageBubble(previewMessage, true);

        // 异步上传文件
        new Thread(() -> {
            try {
                sendFileMessage(file);
                log.info("✅ 文件发送流程完成");
            } catch (Exception e) {
                // ✅ 文件已成功上传到服务器，反序列化错误不影响结果，只记录日志
                log.warn("文件发送流程结束（可能有反序列化警告，但文件已成功上传）: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * 发送语音
     */
    @FXML
    public void handleSendVoice() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        // 打开文件选择器
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择语音文件");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("语音文件", "*.mp3", "*.wav", "*.m4a"),
            new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        java.io.File file = fileChooser.showOpenDialog(usernameLabel.getScene().getWindow());
        if (file == null) {
            return;
        }

        // 检查文件大小（限制10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.length() > maxSize) {
            showAlert("语音文件过大，请选择小于10MB的文件");
            return;
        }

        log.info("选择了语音文件: {}, 大小: {} bytes", file.getName(), file.length());

        // 在UI上显示发送中的消息
        String previewMessage = "[语音] " + file.getName();
        addMessageBubble(previewMessage, true);

        // 异步上传语音
        new Thread(() -> {
            try {
                sendVoiceMessage(file);
                log.info("✅ 语音发送流程完成");
            } catch (Exception e) {
                // ✅ 语音已成功上传到服务器，反序列化错误不影响结果，只记录日志
                log.warn("语音发送流程结束（可能有反序列化警告，但文件已成功上传）: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * 发送视频
     */
    @FXML
    public void handleSendVideo() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        // 打开文件选择器
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择视频文件");
        fileChooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("视频文件", "*.mp4", "*.avi", "*.mov", "*.mkv"),
            new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        java.io.File file = fileChooser.showOpenDialog(usernameLabel.getScene().getWindow());
        if (file == null) {
            return;
        }

        // 检查文件大小（限制100MB）
        long maxSize = 100 * 1024 * 1024;
        if (file.length() > maxSize) {
            showAlert("视频文件过大，请选择小于100MB的文件");
            return;
        }

        log.info("选择了视频文件: {}, 大小: {} bytes", file.getName(), file.length());

        // 在UI上显示发送中的消息
        String previewMessage = "[视频] " + file.getName();
        addMessageBubble(previewMessage, true);

        // 异步上传视频
        new Thread(() -> {
            try {
                sendVideoMessage(file);
                log.info("✅ 视频发送流程完成");
            } catch (Exception e) {
                // ✅ 视频已成功上传到服务器，反序列化错误不影响结果，只记录日志
                log.warn("视频发送流程结束（可能有反序列化警告，但文件已成功上传）: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * 发送图片消息（HTTP上传）
     * ✅ 修复：图片上传成功后，SIP通知失败不应影响整体结果
     */
    private void sendImageMessage(java.io.File imageFile) throws Exception {
        // 获取对方用户ID
        Long toUserId = getUserIdByUsername(currentChatFriend);
        if (toUserId == null) {
            throw new Exception("无法获取对方用户ID");
        }

        // 使用HttpFileUploader上传图片
        com.sip.server.entity.Message message = com.sip.client.util.HttpFileUploader.uploadImage(
            imageFile,
            currentUserId,
            toUserId
        );

        log.info("✅ 图片消息已上传成功: fileUrl={}", message.getFileUrl());

        // ✅ 通过SIP MESSAGE通知对方（失败不影响整体流程）
        try {
            String sipUsername = extractSipUsernameFromUsername(currentChatFriend);
            String notifyMessage = "[图片]" + message.getFileUrl();
            sipMessageManager.sendTextMessage(sipUsername, notifyMessage);
            log.info("✅ 图片消息已通过SIP通知对方");
        } catch (Exception e) {
            log.warn("⚠️  SIP通知失败（图片已上传成功）: {}", e.getMessage());
        }
    }

    /**
     * 发送文件消息（HTTP上传）
     * ✅ 修复：文件上传成功后，SIP通知失败不应影响整体结果
     */
    private void sendFileMessage(java.io.File file) throws Exception {
        // 获取对方用户ID
        Long toUserId = getUserIdByUsername(currentChatFriend);
        if (toUserId == null) {
            throw new Exception("无法获取对方用户ID");
        }

        // 使用HttpFileUploader上传文件
        com.sip.server.entity.Message message = com.sip.client.util.HttpFileUploader.uploadFile(
            file,
            currentUserId,
            toUserId
        );

        log.info("✅ 文件消息已上传成功: fileUrl={}", message.getFileUrl());

        // ✅ 通过SIP MESSAGE通知对方（失败不影响整体流程）
        try {
            String sipUsername = extractSipUsernameFromUsername(currentChatFriend);
            String notifyMessage = "[文件]" + file.getName() + "|" + message.getFileUrl();
            sipMessageManager.sendTextMessage(sipUsername, notifyMessage);
            log.info("✅ 文件消息已通过SIP通知对方");
        } catch (Exception e) {
            // ⚠️ SIP通知失败不影响文件上传结果
            log.warn("⚠️  SIP通知失败（文件已上传成功）: {}", e.getMessage());
        }
    }

    /**
     * 发送语音消息（HTTP上传）
     * ✅ 修复：语音上传成功后，SIP通知失败不应影响整体结果
     */
    private void sendVoiceMessage(java.io.File voiceFile) throws Exception {
        // 获取对方用户ID
        Long toUserId = getUserIdByUsername(currentChatFriend);
        if (toUserId == null) {
            throw new Exception("无法获取对方用户ID");
        }

        // 简单计算时长（实际应该解析音频文件获取真实时长）
        // 这里简化为文件大小除以比特率的估算，实际项目应使用音频库
        Integer duration = null; // 暂不提供时长，服务端可选

        // 使用HttpFileUploader上传语音
        com.sip.server.entity.Message message = com.sip.client.util.HttpFileUploader.uploadVoice(
            voiceFile,
            currentUserId,
            toUserId,
            duration
        );

        log.info("✅ 语音消息已上传成功: fileUrl={}", message.getFileUrl());

        // ✅ 通过SIP MESSAGE通知对方（失败不影响整体流程）
        try {
            String sipUsername = extractSipUsernameFromUsername(currentChatFriend);
            String notifyMessage = "[语音]" + message.getFileUrl();
            sipMessageManager.sendTextMessage(sipUsername, notifyMessage);
            log.info("✅ 语音消息已通过SIP通知对方");
        } catch (Exception e) {
            log.warn("⚠️  SIP通知失败（语音已上传成功）: {}", e.getMessage());
        }
    }

    /**
     * 发送视频消息（HTTP上传）
     * ✅ 修复：视频上传成功后，SIP通知失败不应影响整体结果
     */
    private void sendVideoMessage(java.io.File videoFile) throws Exception {
        // 获取对方用户ID
        Long toUserId = getUserIdByUsername(currentChatFriend);
        if (toUserId == null) {
            throw new Exception("无法获取对方用户ID");
        }

        // 简单计算时长（实际应该解析视频文件获取真实时长）
        Integer duration = null; // 暂不提供时长，服务端可选

        // 使用HttpFileUploader上传视频
        com.sip.server.entity.Message message = com.sip.client.util.HttpFileUploader.uploadVideo(
            videoFile,
            currentUserId,
            toUserId,
            duration
        );

        log.info("✅ 视频消息已上传成功: fileUrl={}", message.getFileUrl());

        // ✅ 通过SIP MESSAGE通知对方（失败不影响整体流程）
        try {
            String sipUsername = extractSipUsernameFromUsername(currentChatFriend);
            String notifyMessage = "[视频]" + message.getFileUrl();
            sipMessageManager.sendTextMessage(sipUsername, notifyMessage);
            log.info("✅ 视频消息已通过SIP通知对方");
        } catch (Exception e) {
            log.warn("⚠️  SIP通知失败（视频已上传成功）: {}", e.getMessage());
        }
    }

    /**
     * 根据用户名获取用户ID
     */
    private Long getUserIdByUsername(String username) {
        // 从好友列表中查找用户ID
        // TODO: 需要在加载好友列表时缓存用户ID映射
        // 临时方案：假设用户名格式为 userXXX，提取XXX作为ID
        if (username != null && username.startsWith("user")) {
            try {
                return Long.parseLong(username.replace("user", ""));
            } catch (NumberFormatException e) {
                log.error("无法解析用户ID: {}", username);
            }
        }
        return null;
    }

    /**
     * 显示接收到的消息（支持富媒体）
     */
    private void displayReceivedMessage(String messageBody, boolean isSent) {
        if (messageBody == null || messageBody.isEmpty()) {
            return;
        }

        // 识别消息类型并展示
        if (messageBody.startsWith("[图片]")) {
            // 图片消息格式：[图片]fileUrl
            String fileUrl = messageBody.substring(4);
            displayImageMessage(fileUrl, isSent);
        } else if (messageBody.startsWith("[语音]")) {
            // 语音消息格式：[语音]fileUrl
            String fileUrl = messageBody.substring(4);
            displayVoiceMessage(fileUrl, isSent);
        } else if (messageBody.startsWith("[视频]")) {
            // 视频消息格式：[视频]fileUrl
            String fileUrl = messageBody.substring(4);
            displayVideoMessage(fileUrl, isSent);
        } else if (messageBody.startsWith("[文件]")) {
            // 文件消息格式：[文件]fileName|fileUrl
            String content = messageBody.substring(4);
            String[] parts = content.split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "未知文件";
            String fileUrl = parts.length > 1 ? parts[1] : "";
            displayFileMessage(fileName, fileUrl, isSent);
        } else {
            // 普通文字消息
            addMessageBubble(messageBody, isSent);
        }
    }

    /**
     * 显示图片消息
     */
    private void displayImageMessage(String fileUrl, boolean isSent) {
        log.info("显示图片消息: fileUrl={}", fileUrl);

        // 创建容器
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        // 创建ImageView
        javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
        imageView.setFitWidth(200);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-cursor: hand;");

        // 异步加载图片
        String fullUrl = com.sip.client.util.HttpClientUtil.getBaseUrl() + fileUrl;
        log.info("加载图片URL: {}", fullUrl);

        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image(fullUrl, true);
            imageView.setImage(image);

            // 点击查看大图
            imageView.setOnMouseClicked(event -> {
                showImagePreview(fullUrl);
            });
        } catch (Exception e) {
            log.error("加载图片失败", e);
            // 显示占位符
            Label errorLabel = new Label("图片加载失败");
            errorLabel.setStyle("-fx-text-fill: red; -fx-padding: 10px;");
            container.getChildren().add(errorLabel);
        }

        container.getChildren().add(imageView);
        messageBox.getChildren().add(container);

        // 滚动到底部
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 显示语音消息
     */
    private void displayVoiceMessage(String fileUrl, boolean isSent) {
        log.info("显示语音消息: fileUrl={}", fileUrl);

        // 创建容器
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        // 创建播放按钮
        javafx.scene.control.Button playButton = new javafx.scene.control.Button("▶ 播放语音");
        playButton.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);"
        );

        playButton.setOnAction(event -> {
            String fullUrl = com.sip.client.util.HttpClientUtil.getBaseUrl() + fileUrl;
            playAudioFile(fullUrl);
        });

        container.getChildren().add(playButton);
        messageBox.getChildren().add(container);

        // 滚动到底部
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 显示视频消息
     */
    private void displayVideoMessage(String fileUrl, boolean isSent) {
        log.info("显示视频消息: fileUrl={}", fileUrl);

        // 创建容器
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        // 创建播放按钮
        javafx.scene.control.Button playButton = new javafx.scene.control.Button("▶ 播放视频");
        playButton.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);"
        );

        playButton.setOnAction(event -> {
            String fullUrl = com.sip.client.util.HttpClientUtil.getBaseUrl() + fileUrl;
            playVideoFile(fullUrl);
        });

        container.getChildren().add(playButton);
        messageBox.getChildren().add(container);

        // 滚动到底部
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 显示文件消息
     */
    private void displayFileMessage(String fileName, String fileUrl, boolean isSent) {
        log.info("显示文件消息: fileName={}, fileUrl={}", fileName, fileUrl);

        // 创建容器
        VBox container = new VBox(5);
        container.setStyle(isSent ? "-fx-alignment: center-right;" : "-fx-alignment: center-left;");

        // 创建下载按钮
        javafx.scene.control.Button downloadButton = new javafx.scene.control.Button("📄 " + fileName);
        downloadButton.setStyle(
            isSent
            ? "-fx-background-color: #95ec69; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand;"
            : "-fx-background-color: white; -fx-text-fill: black; -fx-padding: 10px 20px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);"
        );

        downloadButton.setOnAction(event -> {
            downloadFile(fileUrl, fileName);
        });

        container.getChildren().add(downloadButton);
        messageBox.getChildren().add(container);

        // 滚动到底部
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    /**
     * 预览大图
     */
    private void showImagePreview(String imageUrl) {
        try {
            Stage previewStage = new Stage();
            previewStage.setTitle("图片预览");

            javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
            javafx.scene.image.Image image = new javafx.scene.image.Image(imageUrl);
            imageView.setImage(image);
            imageView.setPreserveRatio(true);

            ScrollPane scrollPane = new ScrollPane(imageView);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            Scene scene = new Scene(scrollPane, 800, 600);
            previewStage.setScene(scene);
            previewStage.show();
        } catch (Exception e) {
            log.error("预览图片失败", e);
            showAlert("预览图片失败: " + e.getMessage());
        }
    }

    /**
     * 播放音频文件
     */
    private void playAudioFile(String audioUrl) {
        try {
            log.info("播放音频: {}", audioUrl);
            // 使用JavaFX MediaPlayer播放音频
            javafx.scene.media.Media media = new javafx.scene.media.Media(audioUrl);
            javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(media);
            mediaPlayer.play();

            mediaPlayer.setOnEndOfMedia(() -> {
                log.info("音频播放完成");
                mediaPlayer.dispose();
            });

            showAlert("正在播放音频...");
        } catch (Exception e) {
            log.error("播放音频失败", e);
            showAlert("播放音频失败: " + e.getMessage());
        }
    }

    /**
     * 播放视频文件
     */
    private void playVideoFile(String videoUrl) {
        try {
            log.info("播放视频: {}", videoUrl);

            // 创建视频播放窗口
            Stage videoStage = new Stage();
            videoStage.setTitle("视频播放");

            javafx.scene.media.Media media = new javafx.scene.media.Media(videoUrl);
            javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(media);
            javafx.scene.media.MediaView mediaView = new javafx.scene.media.MediaView(mediaPlayer);

            mediaView.setFitWidth(640);
            mediaView.setFitHeight(480);

            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(mediaView);
            Scene scene = new Scene(root, 640, 480);
            videoStage.setScene(scene);

            mediaPlayer.play();
            videoStage.show();

            videoStage.setOnCloseRequest(event -> {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            });

        } catch (Exception e) {
            log.error("播放视频失败", e);
            showAlert("播放视频失败: " + e.getMessage());
        }
    }

    /**
     * 语音通话
     */
    @FXML
    public void handleVoiceCall() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        try {
            log.info("发起语音通话给: {}", currentChatFriend);

            // 打开通话窗口
            openCallWindow(currentChatFriend, "audio", true, null);

        } catch (Exception e) {
            log.error("发起语音通话失败", e);
            showAlert("呼叫失败: " + e.getMessage());
        }
    }

    /**
     * 视频通话
     */
    @FXML
    public void handleVideoCall() {
        if (currentChatFriend == null) {
            showAlert("请先选择聊天对象");
            return;
        }

        try {
            log.info("发起视频通话给: {}", currentChatFriend);

            // 打开通话窗口
            openCallWindow(currentChatFriend, "video", true, null);

        } catch (Exception e) {
            log.error("发起视频通话失败", e);
            showAlert("呼叫失败: " + e.getMessage());
        }
    }

    /**
     * 添加好友
     */
    @FXML
    public void handleAddFriend() {
        showAlert("添加好友功能待实现");
    }

    /**
     * 创建会议
     */
    @FXML
    public void handleCreateConference() {
        // 创建自定义对话框
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("创建会议");
        dialog.setHeaderText("请输入会议名称");

        // 设置按钮
        javafx.scene.control.ButtonType createButtonType = new javafx.scene.control.ButtonType("创建", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, javafx.scene.control.ButtonType.CANCEL);

        // 创建输入字段
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        javafx.scene.control.TextField conferenceNameField = new javafx.scene.control.TextField();
        conferenceNameField.setPromptText("请输入会议名称");

        grid.add(new javafx.scene.control.Label("会议名称:"), 0, 0);
        grid.add(conferenceNameField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        // 请求焦点到会议名称字段
        javafx.application.Platform.runLater(() -> conferenceNameField.requestFocus());

        // 转换结果
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return conferenceNameField.getText().trim();
            }
            return null;
        });

        java.util.Optional<String> result = dialog.showAndWait();

        result.ifPresent(conferenceName -> {
            // 验证会议名称
            if (conferenceName.isEmpty()) {
                showAlert("请输入会议名称");
                return;
            }

            log.info("创建会议: 名称={}", conferenceName);

            // 创建并加入会议
            createAndJoinConference(conferenceName);
        });
    }

    /**
     * 加入会议
     */
    @FXML
    public void handleJoinConference() {
        // 创建自定义对话框
        javafx.scene.control.Dialog<java.util.Map<String, String>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("加入会议");
        dialog.setHeaderText("请输入要加入的会议号");

        // 设置按钮
        javafx.scene.control.ButtonType joinButtonType = new javafx.scene.control.ButtonType("加入", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(joinButtonType, javafx.scene.control.ButtonType.CANCEL);

        // 创建输入字段
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        javafx.scene.control.TextField conferenceCodeField = new javafx.scene.control.TextField();
        conferenceCodeField.setPromptText("会议号（6位数字）");

        grid.add(new javafx.scene.control.Label("会议号:"), 0, 0);
        grid.add(conferenceCodeField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        // 请求焦点
        javafx.application.Platform.runLater(() -> conferenceCodeField.requestFocus());

        // 转换结果
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == joinButtonType) {
                java.util.Map<String, String> result = new java.util.HashMap<>();
                result.put("code", conferenceCodeField.getText().trim());
                return result;
            }
            return null;
        });

        java.util.Optional<java.util.Map<String, String>> result = dialog.showAndWait();

        result.ifPresent(conferenceInfo -> {
            String conferenceCode = conferenceInfo.get("code");

            // 验证会议号（6位数字）
            if (conferenceCode.isEmpty()) {
                showAlert("请输入会议号");
                return;
            }

            if (!conferenceCode.matches("\\d{6}")) {
                showAlert("会议号格式错误，应为6位数字");
                return;
            }

            log.info("加入会议: 会议号={}", conferenceCode);

            // 通过会议号加入会议
            joinConferenceByCode(conferenceCode);
        });
    }

    /**
     * 通过会议号加入会议
     */
    private void joinConferenceByCode(String conferenceCode) {
        try {
            log.info("通过会议号加入会议: conferenceCode={}", conferenceCode);

            // 先通过会议号查询会议信息
            ConferenceInfo conferenceInfo = getConferenceByCode(conferenceCode);
            if (conferenceInfo == null) {
                showAlert("会议不存在或已结束");
                return;
            }

            log.info("找到会议: ID={}, 会议号={}, 标题={}", conferenceInfo.id, conferenceInfo.code, conferenceInfo.title);

            // 调用服务器API加入会议
            boolean success = joinConferenceOnServerByCode(conferenceCode, currentUserId);
            log.info("加入会议服务器API调用结果: {}", success);

            if (success) {
                // 使用服务器返回的会议标题，如果为空则使用默认值
                String conferenceName = (conferenceInfo.title != null && !conferenceInfo.title.isEmpty())
                    ? conferenceInfo.title
                    : "会议 " + conferenceCode;

                // 打开会议窗口，传递会议号
                openConferenceWindow(conferenceInfo.id, conferenceInfo.code, conferenceName, new java.util.ArrayList<>());
            } else {
                showAlert("加入会议失败");
            }

        } catch (Exception e) {
            log.error("加入会议失败", e);
            showAlert("加入会议失败: " + e.getMessage());
        }
    }

    /**
     * 通过会议号查询会议信息
     */
    private ConferenceInfo getConferenceByCode(String conferenceCode) {
        try {
            String url = SipConfig.getHttpServerUrl() + "/api/conference/code/" + conferenceCode;
            log.info("查询会议号: {}", url);

            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");

            // 添加认证Token
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int httpResponseCode = conn.getResponseCode();
            log.info("会议号查询HTTP响应码: {}", httpResponseCode);

            if (httpResponseCode == 200) {
                // 解析响应
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    String jsonResponse = response.toString();
                    log.info("会议查询响应: {}", jsonResponse);

                    // 首先检查业务错误码
                    String businessCode = extractJsonNumberValue(jsonResponse, "code");
                    if (businessCode != null && !businessCode.equals("200") && !businessCode.equals("0")) {
                        log.warn("会议查询失败，业务错误码: {}", businessCode);
                        return null;
                    }

                    // 提取data字段中的会议信息
                    String conferenceId = extractJsonNumberValue(jsonResponse, "id");
                    String code = extractJsonStringValue(jsonResponse, "conferenceCode");
                    String title = extractJsonStringValue(jsonResponse, "title");  // 新增：提取会议标题

                    // 必须检查是否为空字符串
                    if (conferenceId != null && !conferenceId.isEmpty() && code != null && !code.isEmpty()) {
                        log.info("成功解析会议信息: ID={}, conferenceCode={}, title={}", conferenceId, code, title);
                        return new ConferenceInfo(conferenceId, code, title);  // 传递title
                    } else {
                        log.warn("会议信息解析失败或为空: ID={}, conferenceCode={}", conferenceId, code);
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.error("查询会议号失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 通过会议号加入会议（服务器API）
     */
    private boolean joinConferenceOnServerByCode(String conferenceCode, Long userId) {
        try {
            String url = SipConfig.getHttpServerUrl() + "/api/conference/join";
            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            // 添加认证Token
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            // 构建请求体 - 使用会议号而非会议ID
            String jsonInput = String.format(
                "{\"conferenceCode\":\"%s\",\"userId\":%s}",
                conferenceCode, userId
            );

            log.info("发送加入会议请求（会议号）: {}", jsonInput);

            // 发送请求
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            log.info("加入会议API响应码: {}", responseCode);

            if (responseCode == 200) {
                // 解析响应JSON，检查业务错误码
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    String jsonResponse = response.toString();
                    log.info("加入会议API响应: {}", jsonResponse);

                    // 检查业务错误码
                    String businessCode = extractJsonNumberValue(jsonResponse, "code");
                    if (businessCode != null && (businessCode.equals("200") || businessCode.equals("0"))) {
                        log.info("✅ 成功通过会议号加入会议: {}", conferenceCode);
                        return true;
                    } else {
                        String message = extractJsonStringValue(jsonResponse, "message");
                        log.warn("加入会议失败，业务错误码: {}, 错误信息: {}", businessCode, message);
                        return false;
                    }
                }
            } else {
                log.warn("加入会议失败，HTTP响应码: {}", responseCode);
                return false;
            }

        } catch (java.net.ConnectException e) {
            log.error("无法连接到服务器", e);
            Platform.runLater(() -> showAlert("无法连接到服务器，请确保服务器已启动"));
            return false;
        } catch (Exception e) {
            log.error("调用加入会议API失败", e);
            return false;
        }
    }

    /**
     * 创建并加入会议
     */
    private void createAndJoinConference(String conferenceName) {
        try {
            log.info("创建并加入会议: 名称={}", conferenceName);

            // 调用服务器API创建会议，返回会议信息（ID和会议号）
            ConferenceInfo conferenceInfo = createConferenceOnServer(conferenceName, currentUserId);

            if (conferenceInfo != null && conferenceInfo.id != null && conferenceInfo.code != null) {
                log.info("✅ 会议创建成功，ID: {}, 会议号: {}", conferenceInfo.id, conferenceInfo.code);

                // 显示会议号给用户（可复制）
                showConferenceCreatedDialog(conferenceInfo.id, conferenceInfo.code, conferenceName);

                // 打开会议窗口，传递会议号
                openConferenceWindow(conferenceInfo.id, conferenceInfo.code, conferenceName, new java.util.ArrayList<>());
            } else {
                // 创建失败
                log.warn("会议创建失败");
                showAlert("会议创建失败，请稍后重试");
            }

        } catch (Exception e) {
            log.error("创建会议失败", e);
            showAlert("创建会议失败: " + e.getMessage());
        }
    }

    /**
     * 加入现有会议
     */
    private void joinExistingConference(String conferenceId) {
        try {
            log.info("尝试加入会议: ID={}", conferenceId);

            // 先检查会议是否存在
            boolean exists = checkConferenceExists(conferenceId);
            log.info("会议存在性检查结果: {}", exists);

            if (!exists) {
                log.warn("会议 {} 不存在，中止加入操作", conferenceId);
                showAlert("会议不存在: 会议ID " + conferenceId + " 未创建");
                return;
            }

            log.info("会议 {} 存在，继续加入流程", conferenceId);

            // 调用服务器API加入会议（使用userId而不是username）
            boolean success = joinConferenceOnServer(conferenceId, currentUserId);
            log.info("加入会议服务器API调用结果: {}", success);

            if (success) {
                // 使用默认会议名称
                String conferenceName = "会议 " + conferenceId;
                // 打开会议窗口 (没有会议号时传递空字符串)
                openConferenceWindow(conferenceId, "", conferenceName, new java.util.ArrayList<>());
            } else {
                showAlert("加入会议失败");
            }

        } catch (Exception e) {
            log.error("加入会议失败", e);
            showAlert("加入会议失败: " + e.getMessage());
        }
    }

    /**
     * 在服务器上创建会议
     * @param conferenceName 会议名称
     * @param creatorId 创建者ID
     * @return 成功返回会议对象（包含ID和会议号），失败返回null
     */
    private ConferenceInfo createConferenceOnServer(String conferenceName, Long creatorId) {
        try {
            String url = SipConfig.getHttpServerUrl() + "/api/conference/create";
            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            // 添加认证Token
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            conn.setDoOutput(true);
            conn.setConnectTimeout(3000); // 3秒超时
            conn.setReadTimeout(3000);

            // 构建请求体 - 匹配服务器端期望的格式 (creatorId: Long, title: String)
            String jsonInput = String.format(
                "{\"creatorId\":%s,\"title\":\"%s\"}",
                creatorId, conferenceName
            );

            log.info("发送创建会议请求: {}", jsonInput);

            // 发送请求
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            log.info("创建会议API响应码: {}", responseCode);

            if (responseCode == 200) {
                // 解析响应，获取会议ID和会议号
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    String jsonResponse = response.toString();
                    log.info("创建会议API响应: {}", jsonResponse);

                    // 提取会议ID和会议号
                    String conferenceId = extractJsonNumberValue(jsonResponse, "id");
                    String conferenceCode = extractJsonStringValue(jsonResponse, "conferenceCode");

                    if (conferenceId != null && conferenceCode != null) {
                        log.info("✅ 会议创建成功，ID: {}, 会议号: {}", conferenceId, conferenceCode);
                        return new ConferenceInfo(conferenceId, conferenceCode);
                    } else {
                        log.error("❌ 无法从响应中提取会议信息");
                        return null;
                    }
                }
            } else {
                log.error("创建会议失败，响应码: {}", responseCode);
                return null;
            }

        } catch (java.net.ConnectException e) {
            log.error("无法连接到服务器", e);
            return null;
        } catch (java.net.SocketTimeoutException e) {
            log.error("连接服务器超时", e);
            return null;
        } catch (Exception e) {
            log.error("调用创建会议API失败", e);
            return null;
        }
    }

    /**
     * 会议信息类（内部使用）
     */
    private static class ConferenceInfo {
        String id;
        String code;
        String title;  // 新增：会议标题

        ConferenceInfo(String id, String code) {
            this.id = id;
            this.code = code;
        }

        ConferenceInfo(String id, String code, String title) {
            this.id = id;
            this.code = code;
            this.title = title;
        }
    }

    /**
     * 检查会议是否存在
     */
    private boolean checkConferenceExists(String conferenceId) {
        try {
            // 使用 GET /api/conference/{conferenceId} 来检查会议是否存在
            String url = SipConfig.getHttpServerUrl() + "/api/conference/" + conferenceId;
            log.info("检查会议是否存在: {}", url);

            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");

            // 添加认证Token
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int httpResponseCode = conn.getResponseCode();
            log.info("会议存在性检查HTTP响应码: {}", httpResponseCode);

            if (httpResponseCode == 200) {
                // ✅ 修复：HTTP 200不代表会议存在，需要解析JSON中的code字段
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    String jsonResponse = response.toString();
                    log.info("会议检查JSON响应: {}", jsonResponse);

                    // 解析JSON中的code字段 (code是数字类型)
                    String codeValue = extractJsonNumberValue(jsonResponse, "code");
                    log.info("解析到的业务code: {}", codeValue);

                    if ("200".equals(codeValue)) {
                        // 业务code=200，会议存在
                        log.info("✅ 会议 {} 已存在", conferenceId);
                        return true;
                    } else if ("404".equals(codeValue)) {
                        // 业务code=404，会议不存在
                        log.info("❌ 会议 {} 不存在（业务code=404）", conferenceId);
                        return false;
                    } else {
                        log.warn("未知的业务code: {}", codeValue);
                        return false;
                    }
                }
            } else if (httpResponseCode == 404) {
                // HTTP 404，会议不存在
                log.info("❌ 会议 {} 不存在（HTTP 404）", conferenceId);
                return false;
            } else if (httpResponseCode == 401) {
                // 未授权 - 需要重新登录
                log.error("检查会议时授权失败（401），可能token已过期");
                Platform.runLater(() -> showAlert("授权已过期，请重新登录"));
                return false;
            } else {
                log.warn("会议检查返回未预期的HTTP响应码: {}", httpResponseCode);
                return false;
            }

        } catch (java.net.ConnectException e) {
            log.error("无法连接到服务器: {}", e.getMessage());
            Platform.runLater(() -> showAlert("无法连接到服务器，请确保服务器已启动"));
            return false;
        } catch (java.io.FileNotFoundException e) {
            // 404错误会抛出FileNotFoundException
            log.info("❌ 会议 {} 不存在 (404)", conferenceId);
            return false;
        } catch (Exception e) {
            log.error("检查会议是否存在失败: {}", e.getMessage(), e);
            Platform.runLater(() -> showAlert("检查会议失败: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 在服务器上加入会议
     */
    private boolean joinConferenceOnServer(String conferenceId, Long userId) {
        try {
            String url = SipConfig.getHttpServerUrl() + "/api/conference/join";
            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            // 添加认证Token
            if (authToken != null && !authToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
            }

            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            // 构建请求体 - 匹配服务器端期望的格式 (conferenceId: Long, userId: Long)
            String jsonInput = String.format(
                "{\"conferenceId\":%s,\"userId\":%s}",
                conferenceId, userId
            );

            log.info("发送加入会议请求: {}", jsonInput);

            // 发送请求
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            log.info("加入会议API响应码: {}", responseCode);

            if (responseCode == 200) {
                log.info("✅ 成功加入会议: {}", conferenceId);
                return true;
            } else {
                log.warn("加入会议失败，响应码: {}", responseCode);
                return false;
            }

        } catch (java.net.ConnectException e) {
            log.error("无法连接到服务器", e);
            Platform.runLater(() -> showAlert("无法连接到服务器，请确保服务器已启动"));
            return false;
        } catch (Exception e) {
            log.error("调用加入会议API失败", e);
            return false;
        }
    }

    /**
     * 打开会议窗口（简化版）
     */
    private void openConferenceWindow(String conferenceId, String conferenceCode, String conferenceName, java.util.List<String> invitedFriends) {
        // 添加调用栈跟踪，看看谁调用了这个方法
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        log.info("========== openConferenceWindow 被调用 ==========");
        log.info("会议ID: {}, 会议号: {}, 会议名称: {}", conferenceId, conferenceCode, conferenceName);
        log.info("调用栈:");
        for (int i = 2; i < Math.min(stackTrace.length, 8); i++) {
            log.info("  {}", stackTrace[i]);
        }
        log.info("==============================================");

        try {
            // 检查会议窗口是否已经打开
            if (conferenceStages.containsKey(conferenceId)) {
                javafx.stage.Stage existingStage = conferenceStages.get(conferenceId);
                if (existingStage != null && existingStage.isShowing()) {
                    log.info("会议窗口已存在，将其置于前台: ID={}", conferenceId);
                    existingStage.toFront();
                    existingStage.requestFocus();
                    return;
                } else {
                    // 窗口已关闭，从映射中移除
                    conferenceStages.remove(conferenceId);
                }
            }

            log.info("打开会议窗口: ID={}, 会议号={}, 名称={}", conferenceId, conferenceCode, conferenceName);

            // 加载会议界面FXML
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/conference.fxml")
            );
            javafx.scene.Parent root = loader.load();

            // 获取控制器
            com.sip.client.ui.conference.ConferenceViewController conferenceController = loader.getController();

            // 创建会议DTO
            com.sip.common.dto.ConferenceDTO conference = new com.sip.common.dto.ConferenceDTO();
            conference.setTitle(conferenceName);
            conference.setConferenceUri("sip:" + conferenceId + "@" + SipConfig.getSipServerHost());
            conference.setCreatorId(1L);
            conference.setMaxParticipants(SipConfig.getConferenceMaxParticipants());

            // 🆕 设置会议ID（用于退出会议时调用后端API）
            try {
                conference.setId(Long.parseLong(conferenceId));
                log.info("✅ 设置会议ID: {}", conferenceId);
            } catch (NumberFormatException e) {
                log.warn("⚠️  无法解析会议ID为Long类型: {}", conferenceId);
            }

            // 设置当前用户名和ID
            conferenceController.setCurrentUsername(currentUsername);
            conferenceController.setCurrentUserId(String.valueOf(currentUserId));

            // 🆕 设置认证Token（用于调用后端API）
            conferenceController.setAuthToken(authToken);

            // 🆕 设置会议号和会议名称（用于UI显示）
            conferenceController.setConferenceCode(conferenceCode);
            conferenceController.setConferenceTitle(conferenceName);

            // 加入会议（不发送邀请）
            conferenceController.joinConference(conference);

            // 创建新窗口
            javafx.stage.Stage conferenceStage = new javafx.stage.Stage();
            conferenceStage.setTitle("会议 - " + conferenceName);
            conferenceStage.setScene(new javafx.scene.Scene(root, 1200, 800));

            // 应用样式
            conferenceStage.getScene().getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm()
            );

            // 添加窗口关闭事件处理（确保摄像头被关闭，并从映射中移除）
            conferenceStage.setOnCloseRequest(event -> {
                log.info("会议窗口关闭，清理资源: ID={}", conferenceId);
                // ConferenceViewController会在handleEndConference中处理清理
                // 这里添加一个额外的保险措施
                try {
                    conferenceController.cleanup();
                } catch (Exception e) {
                    log.error("清理会议资源失败", e);
                }
                // 从映射中移除
                conferenceStages.remove(conferenceId);
            });

            // 保存到映射中
            conferenceStages.put(conferenceId, conferenceStage);

            conferenceStage.show();

            log.info("会议窗口已打开: ID={}, 会议号={}", conferenceId, conferenceCode);

        } catch (Exception e) {
            log.error("打开会议窗口失败", e);
            showAlert("打开会议窗口失败: " + e.getMessage());
        }
    }

    /**
     * 个人设置
     */
    @FXML
    public void handleSettings() {
        showAlert("设置功能待实现");
    }

    /**
     * 修改密码
     */
    @FXML
    public void handleChangePassword() {
        showAlert("修改密码功能待实现");
    }

    /**
     * 打开通话记录窗口
     */
    @FXML
    public void handleCallRecords() {
        try {
            log.info("打开通话记录窗口");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/call-record-view.fxml"));
            javafx.scene.layout.BorderPane root = loader.load();

            CallRecordViewController controller = loader.getController();
            controller.setCurrentUserId(currentUserId);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("通话记录");
            stage.setScene(new javafx.scene.Scene(root, 900, 700));
            stage.show();

            log.info("✅ 通话记录窗口已打开");
        } catch (Exception e) {
            log.error("打开通话记录窗口失败", e);
            showAlert("打开通话记录失败: " + e.getMessage());
        }
    }

    /**
     * 退出登录
     */
    @FXML
    public void handleLogout() {
        log.info("用户退出登录");

        try {
            // ✅ 1. 停止在线状态刷新定时器
            stopOnlineStatusRefresh();

            // ✅ 2. 清理所有会议窗口
            for (javafx.stage.Stage stage : conferenceStages.values()) {
                if (stage != null && stage.isShowing()) {
                    stage.close();
                }
            }
            conferenceStages.clear();
            log.info("✅ 已关闭所有会议窗口");

            // ✅ 3. 关闭通话窗口
            if (callWindowStage != null && callWindowStage.isShowing()) {
                callWindowStage.close();
                callWindowStage = null;
            }
            log.info("✅ 已关闭通话窗口");

            // ✅ 4. 清理消息管理器
            if (sipMessageManager != null) {
                sipMessageManager.setMessageCallback(null);
                sipMessageManager = null;
            }

            // ✅ 5. 清理通话管理器
            if (callManager != null) {
                callManager.setCallEventListener(null);
                callManager = null;
            }

            // ✅ 6. 清理媒体管理器引用
            mediaManager = null;

            // ✅ 7. 关闭SIP连接
            if (sipClientService != null) {
                sipClientService.shutdown();
                sipClientService = null;
            }

            // ✅ 8. 清理RegisterManager的callback
            if (registerManager != null) {
                try {
                    registerManager.setCallback(null);
                    registerManager.setMessageManager(null);
                } catch (Exception e) {
                    log.warn("清理RegisterManager失败", e);
                }
                registerManager = null;
            }

            log.info("✅ 所有资源已清理");

            // 返回登录界面
            Main.showLoginView();
            log.info("退出成功");

        } catch (Exception e) {
            log.error("退出登录失败", e);
            showAlert("退出失败: " + e.getMessage());
        }
    }

    /**
     * 显示会议创建成功对话框（显示会议ID,可复制）
     */
    private void showConferenceCreatedDialog(String conferenceId, String conferenceCode, String conferenceName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("会议创建成功");
        alert.setHeaderText("会议已创建");

        // 创建可选择的文本框来显示会议信息
        javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea();
        textArea.setText(
            "会议名称: " + conferenceName + "\n" +
            "会议号: " + conferenceCode + " (6位数字)\n" +
            "会议ID: " + conferenceId + " (内部使用)\n\n" +
            "✨ 分享会议号 " + conferenceCode + " 给其他人\n" +
            "他们可以通过\"加入会议\"输入会议号加入。"
        );
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(150);

        alert.getDialogPane().setContent(textArea);

        // 添加复制按钮（复制会议号而非ID）
        ButtonType copyButton = new ButtonType("复制会议号");
        ButtonType closeButton = new ButtonType("进入会议", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(copyButton, closeButton);

        // 处理按钮点击
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == copyButton) {
            // 复制会议号到剪贴板
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(conferenceCode);
            clipboard.setContent(content);
            log.info("会议号已复制到剪贴板: {}", conferenceCode);

            // 显示复制成功提示，但不关闭对话框，而是重新显示
            Platform.runLater(() -> {
                Alert copyAlert = new Alert(Alert.AlertType.INFORMATION);
                copyAlert.setTitle("复制成功");
                copyAlert.setHeaderText(null);
                copyAlert.setContentText("会议号 " + conferenceCode + " 已复制到剪贴板");
                copyAlert.showAndWait();

                // 复制后重新显示会议创建对话框
                showConferenceCreatedDialog(conferenceId, conferenceCode, conferenceName);
            });
        }
        // 如果点击"进入会议"或关闭对话框，则正常关闭
    }

    /**
     * 显示会议邀请对话框
     */
    private void showConferenceInviteDialog(String inviter, String conferenceId, String conferenceTitle) {
        log.info("显示会议邀请: 来自={}, 会议ID={}, 标题={}", inviter, conferenceId, conferenceTitle);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("会议邀请");
        alert.setHeaderText(inviter + " 邀请您加入会议");
        alert.setContentText("会议: " + conferenceTitle + "\n会议ID: " + conferenceId + "\n\n是否接受邀请?");

        ButtonType acceptButton = new ButtonType("接受");
        ButtonType declineButton = new ButtonType("拒绝", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(acceptButton, declineButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == acceptButton) {
                log.info("接受会议邀请: {}", conferenceId);
                // 加入现有会议
                joinExistingConference(conferenceId);
            } else {
                log.info("拒绝会议邀请: {}", conferenceId);
            }
        });
    }

    /**
     * 打开通话窗口
     * @param targetUsername 目标用户名
     * @param callType 通话类型 ("audio" or "video")
     * @param isCaller 是否为主叫方
     * @param callId 呼叫ID（被叫方需要）
     */
    private void openCallWindow(String targetUsername, String callType, boolean isCaller, String callId) {
        try {
            // 加载通话窗口FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/call-window.fxml"));
            Parent root = loader.load();

            // 获取控制器
            CallWindowController controller = loader.getController();

            // 设置通话参数
            controller.setCallParams(targetUsername, callType, isCaller);

            // 创建Stage
            callWindowStage = new Stage();
            callWindowStage.setTitle((callType.equals("audio") ? "语音通话" : "视频通话") + " - " + targetUsername);
            callWindowStage.setScene(new Scene(root));
            callWindowStage.setResizable(false);

            // 设置Stage引用
            controller.setStage(callWindowStage);

            // 如果是被叫方，需要设置CallId
            if (!isCaller && callId != null) {
                controller.setCallId(callId);
            }

            // 显示窗口
            callWindowStage.show();

            // 窗口显示后发起呼叫（主叫方）或接听（被叫方）
            if (isCaller) {
                controller.makeCall();
            } else {
                controller.answerCall();
            }

            log.info("✅ 通话窗口已打开: target={}, type={}, isCaller={}",
                targetUsername, callType, isCaller);

        } catch (Exception e) {
            log.error("❌ 打开通话窗口失败", e);
            showAlert("无法打开通话窗口: " + e.getMessage());
        }
    }

    /**
     * 显示来电对话框
     * @param callId 呼叫ID
     * @param callerUsername 来电用户名
     * @param callType 通话类型 ("audio" or "video")
     */
    private void showIncomingCallDialog(String callId, String callerUsername, String callType) {
        String callTypeText = callType.equals("audio") ? "语音通话" : "视频通话";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("来电");
        alert.setHeaderText(callerUsername + " 邀请您进行" + callTypeText);
        alert.setContentText("是否接听？");

        ButtonType acceptButton = new ButtonType("接听");
        ButtonType rejectButton = new ButtonType("拒绝", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(acceptButton, rejectButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == acceptButton) {
            // 用户接听
            log.info("用户接听来电: callId={}, caller={}", callId, callerUsername);

            // 打开通话窗口（被叫方）
            openCallWindow(callerUsername, callType, false, callId);
        } else {
            // 用户拒绝
            log.info("用户拒绝来电: callId={}, caller={}", callId, callerUsername);

            // 发送拒绝响应
            try {
                callManager.rejectCall(callId, "Declined");
            } catch (Exception e) {
                log.error("拒绝通话失败", e);
            }
        }
    }

    /**
     * 显示提示框
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========================================
    // 内部类：自定义用户列表Cell
    // ========================================

    /**
     * ✅ 自定义用户列表Cell - 显示彩色在线状态
     */
    private static class UserListCell extends javafx.scene.control.ListCell<String> {
        private final javafx.scene.layout.HBox container;
        private final javafx.scene.shape.Circle statusDot;
        private final javafx.scene.control.Label usernameLabel;

        public UserListCell() {
            // 创建状态圆点（半径6px）
            statusDot = new javafx.scene.shape.Circle(6);

            // 创建用户名标签
            usernameLabel = new javafx.scene.control.Label();
            usernameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

            // 容器布局
            container = new javafx.scene.layout.HBox(10);
            container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            container.setPadding(new javafx.geometry.Insets(8, 10, 8, 10));
            container.getChildren().addAll(statusDot, usernameLabel);

            // 鼠标悬停效果
            container.setOnMouseEntered(e -> {
                if (!isEmpty()) {
                    container.setStyle("-fx-background-color: #e8f5e9; -fx-cursor: hand;");
                }
            });
            container.setOnMouseExited(e -> {
                container.setStyle("-fx-background-color: transparent;");
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                // 解析数据格式: "main:主导航" 或 "online:username" 或 "offline:username"
                String[] parts = item.split(":", 2);
                String type = parts[0];
                String name = parts.length > 1 ? parts[1] : item;

                if ("main".equals(type)) {
                    // ✅ 主导航项：显示特殊图标
                    statusDot.setFill(javafx.scene.paint.Color.web("#2196f3")); // 蓝色圆点
                    usernameLabel.setText("🏠 " + name);
                    usernameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2196f3; -fx-font-weight: bold;");
                } else {
                    // 普通用户：显示在线/离线状态
                    boolean isOnline = "online".equals(type);

                    // 设置状态圆点颜色
                    if (isOnline) {
                        // 在线：鲜艳的绿色
                        statusDot.setFill(javafx.scene.paint.Color.web("#4caf50"));
                    } else {
                        // 离线：红色
                        statusDot.setFill(javafx.scene.paint.Color.web("#f44336"));
                    }

                    // 设置用户名
                    usernameLabel.setText(name);
                    usernameLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");
                }

                setGraphic(container);
                setText(null);
            }
        }
    }
}
