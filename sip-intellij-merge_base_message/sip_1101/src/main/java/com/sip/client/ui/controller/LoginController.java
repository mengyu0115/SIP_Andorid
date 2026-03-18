package com.sip.client.ui.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sip.client.register.SipRegisterManager;
import com.sip.client.ui.service.SipClientService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 登录界面控制器
 *
 * 配置来源：优先从 ../../config.properties 读取，其次使用默认值
 */
@Slf4j
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    private Gson gson = new Gson();

    // 从共享配置读取（如果存在）
    private static final Properties sharedConfig = loadSharedConfig();

    private static final String DEFAULT_SERVER;
    private static final String SIP_SERVER;
    private static final int SIP_PORT;

    static {
        // 从共享配置初始化服务器地址
        String serverIp = sharedConfig.getProperty("SERVER_IP", "10.29.209.85");
        int httpPort = Integer.parseInt(sharedConfig.getProperty("HTTP_PORT", "8081"));
        int sipPort = Integer.parseInt(sharedConfig.getProperty("SIP_PORT", "5060"));

        DEFAULT_SERVER = "http://" + serverIp + ":" + httpPort;
        SIP_SERVER = serverIp;
        SIP_PORT = sipPort;

        log.info("📋 使用共享配置:");
        log.info("   HTTP服务器: {}", DEFAULT_SERVER);
        log.info("   SIP服务器: {}:{}", SIP_SERVER, SIP_PORT);
    }

    /**
     * 加载共享配置文件
     */
    private static Properties loadSharedConfig() {
        Properties props = new Properties();
        try {
            File configFile = new File("../../config.properties");
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
                log.info("✅ 加载共享配置成功: {}", configFile.getCanonicalPath());
            } else {
                log.info("未找到共享配置，使用默认配置");
            }
        } catch (Exception e) {
            log.warn("加载共享配置失败: {}", e.getMessage());
        }
        return props;
    }

    // 标志位，防止重复初始化
    private static boolean sipInitialized = false;
    private static final Object initLock = new Object();

    // 标志位，防止重复打开主窗口
    private boolean mainWindowOpened = false;

    // 保存 registerManager 的引用
    private SipRegisterManager registerManager;

    @FXML
    public void initialize() {
        log.info("登录界面初始化完成");
        log.info("后端服务器: {}", DEFAULT_SERVER);
        log.info("SIP服务器: {}:{}", SIP_SERVER, SIP_PORT);
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("用户名和密码不能为空", true);
            return;
        }

        loginButton.setDisable(true);
        showStatus("正在登录...", false);

        new Thread(() -> {
            try {
                // 调用后端登录API
                Map<String, Object> loginResult = callLoginAPI(DEFAULT_SERVER, username, password);

                if (loginResult == null) {
                    Platform.runLater(() -> {
                        showStatus("登录失败：服务器无响应", true);
                        loginButton.setDisable(false);
                    });
                    return;
                }

                Map<String, Object> userData = (Map<String, Object>) loginResult.get("user");
                if (userData == null) {
                    log.error("登录响应中缺少用户数据");
                    Platform.runLater(() -> {
                        showStatus("登录失败：响应数据格式错误", true);
                        loginButton.setDisable(false);
                    });
                    return;
                }

                String token = (String) loginResult.get("token");  // 提取token

                // 提取用户ID
                Object userIdObj = userData.get("id");
                final Long userId;
                if (userIdObj instanceof Integer) {
                    userId = ((Integer) userIdObj).longValue();
                } else if (userIdObj instanceof Long) {
                    userId = (Long) userIdObj;
                } else if (userIdObj instanceof Double) {
                    userId = ((Double) userIdObj).longValue();
                } else if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else {
                    log.error("无法解析用户ID，类型为: {}", userIdObj != null ? userIdObj.getClass() : "null");
                    userId = null;
                }

                String sipUri = (String) userData.get("sipUri");
                if (sipUri == null || sipUri.isEmpty()) {
                    log.error("用户数据中缺少 sipUri");
                    Platform.runLater(() -> {
                        showStatus("登录失败：SIP URI 信息缺失", true);
                        loginButton.setDisable(false);
                    });
                    return;
                }

                String sipPassword = (String) userData.get("sipPassword");
                if (sipPassword == null || sipPassword.isEmpty()) {
                    log.error("用户数据中缺少 sipPassword");
                    Platform.runLater(() -> {
                        showStatus("登录失败：SIP 密码信息缺失", true);
                        loginButton.setDisable(false);
                    });
                    return;
                }

                final String sipUsername = sipUri.split("@")[0].replace("sip:", "");

                log.info("登录成功：{}, ID: {}, SIP URI: {}, Token: {}", username, userId, sipUri, token != null ? "已获取" : "未获取");

                // ✅ 初始化 HttpClientUtil 配置（用于文件上传、聊天记录加载等）
                if (token != null && !token.isEmpty()) {
                    com.sip.client.util.HttpClientUtil.setBaseUrl(DEFAULT_SERVER);
                    com.sip.client.util.HttpClientUtil.setAuthToken(token);
                    log.info("HttpClientUtil 配置已初始化: baseUrl={}, token={}", DEFAULT_SERVER, token.substring(0, Math.min(10, token.length())) + "...");
                }

                // 获取 SipRegisterManager 单例
                registerManager = SipRegisterManager.getInstance();

                // 只初始化一次 SIP
                synchronized (initLock) {
                    if (!sipInitialized) {
                        // 智能获取本地IP地址
                        String localIp = getPreferredLocalIp();
                        // 使用随机端口避免冲突
                        int localPort = 5061 + (int)(Math.random() * 1000);
                        log.info("初始化SIP协议栈: {}:{}", localIp, localPort);
                        registerManager.initialize(localIp, localPort);
                        sipInitialized = true;
                        log.info("SIP RegisterManager 初始化完成");
                    }
                }

                // 设置回调
                registerManager.setCallback(new SipRegisterManager.RegisterCallback() {
                    @Override
                    public void onRegisterSuccess() {
                        log.info("SIP注册成功");
                        // 只在第一次注册成功时打开主窗口，防止心跳保活时重复打开
                        if (!mainWindowOpened) {
                            mainWindowOpened = true;
                            Platform.runLater(() -> {
                                try {
                                    openMainWindow(username, userId, sipUsername, userData, token);
                                } catch (Exception e) {
                                    log.error("打开主界面失败", e);
                                    showStatus("打开主界面失败", true);
                                    mainWindowOpened = false; // 失败时重置标志
                                }
                            });
                        } else {
                            log.info("主窗口已打开，跳过重复打开");
                        }
                    }

                    @Override
                    public void onRegisterFailed(String reason) {
                        log.error("SIP注册失败: {}", reason);
                        Platform.runLater(() -> {
                            showStatus("SIP注册失败: " + reason, true);
                            loginButton.setDisable(false);
                        });
                    }

                    @Override
                    public void onUnregisterSuccess() {
                        log.info("SIP注销成功");
                    }
                });

                Platform.runLater(() -> showStatus("正在注册到SIP服务器...", false));
                registerManager.register(sipUsername, sipPassword, SIP_SERVER);

            } catch (Exception e) {
                log.error("登录失败", e);
                Platform.runLater(() -> {
                    showStatus("登录失败: " + e.getMessage(), true);
                    loginButton.setDisable(false);
                });
            }
        }).start();
    }

    /**
     * 调用登录API
     */
    private Map<String, Object> callLoginAPI(String serverUrl, String username, String password) {
        try {
            URL url = new URL(serverUrl + "/api/user/login");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // 构造请求体
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("username", username);
            requestBody.put("password", password);

            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                os.write(gson.toJson(requestBody).getBytes("UTF-8"));
            }

            // 读取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                java.io.InputStream is = conn.getInputStream();
                String response = new String(is.readAllBytes(), "UTF-8");

                log.info("登录API响应: {}", response);

                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                if (jsonResponse.get("code").getAsInt() == 200) {
                    Map<String, Object> data = gson.fromJson(jsonResponse.get("data"), Map.class);
                    log.info("解析后的数据: {}", data);
                    return data;
                } else {
                    log.error("登录失败: {}", jsonResponse.get("message").getAsString());
                    return null;
                }
            } else {
                log.error("HTTP请求失败: {}", responseCode);
                return null;
            }

        } catch (Exception e) {
            log.error("调用登录API失败", e);
            return null;
        }
    }

    /**
     * 打开主界面
     */
    private void openMainWindow(String username, Long userId, String sipUsername, Map<String, Object> userData, String token) throws Exception {
        // ✅ 新增：注册在线状态
        registerOnlineStatus(userId, token);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        // 获取MainController并传递数据
        MainController mainController = loader.getController();
        mainController.setCurrentUser(username, userId);  // 传递username和userId
        mainController.setAuthToken(token);  // 传递token

        // 传递 registerManager 用于初始化 SipMessageManager
        mainController.setRegisterManager(registerManager, sipUsername);

        // 创建新窗口
        Stage stage = new Stage();
        stage.setTitle("SIP 即时通信系统 - " + username);
        stage.setScene(new Scene(root, 800, 600));

        // 先显示主窗口
        stage.show();

        // 然后在 JavaFX 线程中延迟关闭登录窗口，确保主窗口已经完全显示
        Platform.runLater(() -> {
            try {
                Stage loginStage = (Stage) loginButton.getScene().getWindow();
                if (loginStage != null) {
                    log.info("关闭登录窗口");
                    loginStage.close();
                } else {
                    log.warn("无法获取登录窗口引用");
                }
            } catch (Exception e) {
                log.error("关闭登录窗口失败", e);
            }
        });
    }

    /**
     * ✅ 新增：注册在线状态到服务器
     */
    private void registerOnlineStatus(Long userId, String token) {
        new Thread(() -> {
            try {
                URL url = new URL(DEFAULT_SERVER + "/api/online/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                // 构造请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("userId", userId);
                requestBody.put("deviceInfo", "Windows PC");

                // 发送请求
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(gson.toJson(requestBody).getBytes("UTF-8"));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    log.info("✅ 用户在线状态注册成功: userId={}", userId);
                } else {
                    log.warn("⚠️ 用户在线状态注册失败: HTTP {}", responseCode);
                }

            } catch (Exception e) {
                log.error("❌ 注册在线状态失败: {}", e.getMessage());
            }
        }).start();
    }

    private void showStatus(String message, boolean isError) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        }
    }

    /**
     * 智能获取本地IP地址 - ✅ 优化版：单次遍历，减少初始化时间
     * 优先选择与SIP服务器在同一网段的IP，避免获取到虚拟网卡IP
     */
    private String getPreferredLocalIp() {
        try {
            // 首先尝试获取与SIP服务器在同一网段的IP（匹配前两段，如 10.129）
            String serverPrefix = SIP_SERVER.substring(0, SIP_SERVER.indexOf('.', SIP_SERVER.indexOf('.') + 1));

            // ✅ 优化：单次遍历，同时记录候选IP，避免扫描两次网卡（耗时2-5秒）
            String fallbackIp = null;

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 跳过未启用、环回或虚拟网卡
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // 只处理IPv4地址
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();

                        // 优先返回与服务器同网段的IP
                        if (ip.startsWith(serverPrefix)) {
                            log.info("找到与SIP服务器同网段的本地IP: {}", ip);
                            return ip;
                        }

                        // 记录第一个有效的IPv4地址作为备选
                        if (fallbackIp == null) {
                            fallbackIp = ip;
                        }
                    }
                }
            }

            // 如果找到了备选IP，使用它
            if (fallbackIp != null) {
                log.info("使用本地IP: {}", fallbackIp);
                return fallbackIp;
            }

        } catch (SocketException e) {
            log.error("获取本地IP失败: {}", e.getMessage());
        }

        // 如果所有方法都失败，使用默认方法
        try {
            String defaultIp = InetAddress.getLocalHost().getHostAddress();
            log.warn("使用默认获取的IP: {}", defaultIp);
            return defaultIp;
        } catch (Exception e) {
            log.error("获取默认IP失败，使用127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}
