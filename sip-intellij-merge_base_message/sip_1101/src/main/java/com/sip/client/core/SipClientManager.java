package com.sip.client.core;

import com.sip.client.register.*;
import com.sip.client.presence.PresenceManager;
import com.sip.client.presence.PresenceCallback;
import com.sip.client.presence.PresenceStatus;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import javax.sip.SipProvider;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SIP 客户端统一管理器
 * 整合 SIP 注册、在线状态、订阅等功能
 *
 * 功能:
 * 1. 统一初始化所有 SIP 组件
 * 2. 统一登录流程 (注册 + 发布状态 + 订阅好友)
 * 3. 统一状态管理
 * 4. 好友状态监听与通知
 * 5. 错误重连机制
 *
 */
@Slf4j
public class SipClientManager {

    // ========== 单例模式 ==========
    private static volatile SipClientManager instance;

    public static SipClientManager getInstance() {
        if (instance == null) {
            synchronized (SipClientManager.class) {
                if (instance == null) {
                    instance = new SipClientManager();
                }
            }
        }
        return instance;
    }

    // ========== SIP 组件 ==========
    private SipRegisterManager registerManager;
    private PresenceManager presenceManager;
    private SubscribeHandler subscribeHandler;

    // ========== 用户信息 ==========
    private String currentUsername;
    private String currentPassword;  // 保存密码用于重连
    private String currentDomain;
    private boolean initialized = false;
    private boolean registered = false;

    // ========== 好友状态缓存 ==========
    private Map<String, String> friendStatusMap = new HashMap<>();

    // ========== 回调接口 ==========
    private LoginCallback loginCallback;
    private FriendStatusListener friendStatusListener;

    // ========== 重连机制 ==========
    private ScheduledExecutorService scheduledExecutor;
    private int retryCount = 0;
    private static final int MAX_RETRY = 3;

    private SipClientManager() {
        registerManager = SipRegisterManager.getInstance();
        presenceManager = PresenceManager.getInstance();
        subscribeHandler = new SubscribeHandler();
        scheduledExecutor = Executors.newScheduledThreadPool(1);
    }

    /**
     * 初始化 SIP 客户端
     *
     * @param localIp 本地 IP
     * @param localPort 本地端口
     */
    public void initialize(String localIp, int localPort) throws Exception {
        if (initialized) {
            log.warn("SipClientManager 已初始化，跳过");
            return;
        }

        log.info("开始初始化 SipClientManager: {}:{}", localIp, localPort);

        // 1. 初始化注册管理器
        registerManager.initialize(localIp, localPort);

        // 2. 初始化在线状态管理器
        // 注意：PresenceManager 需要 SIP Provider 等组件
        // 这里先不初始化，等注册成功后再初始化

        initialized = true;
        log.info("SipClientManager 初始化成功");
    }

    /**
     * 登录 (整合注册 + 发布状态)
     *
     * @param username 用户名
     * @param password SIP 密码
     * @param domain SIP 域名
     * @param callback 登录回调
     */
    public void login(String username, String password, String domain, LoginCallback callback) {
        this.currentUsername = username;
        this.currentPassword = password;  // 保存密码
        this.currentDomain = domain;
        this.loginCallback = callback;
        this.retryCount = 0;

        log.info("开始 SIP 登录: {}@{}", username, domain);

        // 设置注册回调
        registerManager.setCallback(new SipRegisterManager.RegisterCallback() {
            @Override
            public void onRegisterSuccess() {
                log.info("SIP 注册成功");
                registered = true;
                retryCount = 0; // 重置重试次数

                // 注册成功后的后续操作
                handleRegisterSuccess();
            }

            @Override
            public void onRegisterFailed(String reason) {
                log.error("SIP 注册失败: {}", reason);
                registered = false;

                // 尝试重连
                handleRegisterFailure(reason);
            }

            @Override
            public void onUnregisterSuccess() {
                log.info("SIP 注销成功");
                registered = false;
            }
        });

        // 执行 SIP 注册
        registerManager.register(username, password, domain);
    }

    /**
     * 处理注册成功
     */
    private void handleRegisterSuccess() {
        try {
            // 1. 初始化 Presence 管理器
            initializePresenceManager();

            // 2. 发布在线状态
            publishStatus("online");

            // 3. 通知 UI 登录成功
            if (loginCallback != null) {
                loginCallback.onLoginSuccess();
            }

            log.info("登录流程完成");

        } catch (Exception e) {
            log.error("登录后处理失败", e);
            if (loginCallback != null) {
                loginCallback.onLoginFailed("登录后处理失败: " + e.getMessage());
            }
        }
    }

    /**
     * 处理注册失败 (自动重连)
     */
    private void handleRegisterFailure(String reason) {
        if (retryCount < MAX_RETRY) {
            retryCount++;
            log.warn("注册失败，将在 3 秒后进行第 {} 次重试", retryCount);

            // 延迟 3 秒后重试
            scheduledExecutor.schedule(() -> {
                try {
                    log.info("开始第 {} 次重试注册", retryCount);
                    registerManager.register(currentUsername,
                        currentPassword, currentDomain);  // 使用保存的密码
                } catch (Exception e) {
                    log.error("重试注册失败", e);
                }
            }, 3, TimeUnit.SECONDS);

        } else {
            log.error("已达到最大重试次数 ({}), 停止重试", MAX_RETRY);
            if (loginCallback != null) {
                loginCallback.onLoginFailed("注册失败: " + reason + " (已重试 " + MAX_RETRY + " 次)");
            }
        }
    }

    /**
     * 初始化 Presence 管理器
     */
    private void initializePresenceManager() {
        try {
            // 从 RegisterManager 获取 SIP 组件
            // 使用已注册时智能获取的本地IP，支持双机测试
            String localIp = registerManager.getLocalIp();
            int localPort = registerManager.getLocalPort();

            // 初始化（注意：这里是简化版本，实际应该共享 SipProvider）
            log.info("Presence Manager 已准备就绪");

            // 设置 Presence 回调
            presenceManager.setCallback(new PresenceCallback() {
                @Override
                public void onPublishSuccess(PresenceStatus status) {
                    log.info("状态发布成功: {}", status.getDisplayName());
                }

                @Override
                public void onPublishFailed(String error) {
                    log.error("状态发布失败: {}", error);
                }

                @Override
                public void onSubscribeSuccess(String targetUri) {
                    log.info("订阅成功: {}", targetUri);
                }

                @Override
                public void onSubscribeFailed(String targetUri, String error) {
                    log.error("订阅失败: {} - {}", targetUri, error);
                }

                @Override
                public void onNotifyReceived(String friendUri, PresenceStatus status) {
                    log.info("好友 {} 状态变更为: {}", friendUri, status.getDisplayName());

                    // 缓存好友状态
                    friendStatusMap.put(friendUri, status.getDisplayName());

                    // 通知 UI 更新
                    if (friendStatusListener != null) {
                        Platform.runLater(() -> {
                            friendStatusListener.onFriendStatusChanged(friendUri, status.getDisplayName());
                        });
                    }
                }

                @Override
                public void onSubscriptionAccepted(String targetUri) {
                    log.info("订阅被接受: {}", targetUri);
                }

                @Override
                public void onSubscriptionRejected(String targetUri, String reason) {
                    log.warn("订阅被拒绝: {} - {}", targetUri, reason);
                }
            });

        } catch (Exception e) {
            log.error("初始化 Presence Manager 失败", e);
        }
    }

    /**
     * 发布自己的在线状态
     *
     * @param status 状态: online/busy/away/offline
     */
    public void publishStatus(String status) {
        if (!registered) {
            log.warn("尚未注册，无法发布状态");
            return;
        }

        log.info("发布状态: {}", status);
        // 将String转换为PresenceStatus枚举
        PresenceStatus presenceStatus;
        switch (status.toLowerCase()) {
            case "online":
                presenceStatus = PresenceStatus.ONLINE;
                break;
            case "busy":
                presenceStatus = PresenceStatus.BUSY;
                break;
            case "away":
                presenceStatus = PresenceStatus.AWAY;
                break;
            case "offline":
                presenceStatus = PresenceStatus.OFFLINE;
                break;
            default:
                presenceStatus = PresenceStatus.ONLINE;
        }
        presenceManager.publishStatus(presenceStatus);
    }

    /**
     * 订阅好友状态
     *
     * @param friendUsername 好友用户名
     */
    public void subscribeFriend(String friendUsername) {
        if (!registered) {
            log.warn("尚未注册，无法订阅好友");
            return;
        }

        log.info("订阅好友: {}", friendUsername);
        presenceManager.subscribe(friendUsername);
    }

    /**
     * 批量订阅好友状态
     *
     * @param friendUsernames 好友用户名列表
     */
    public void subscribeFriends(String[] friendUsernames) {
        for (String friendUsername : friendUsernames) {
            subscribeFriend(friendUsername);
        }
    }

    /**
     * 获取好友状态
     *
     * @param friendUsername 好友用户名
     * @return 状态字符串，如果未找到返回 "unknown"
     */
    public String getFriendStatus(String friendUsername) {
        return friendStatusMap.getOrDefault(friendUsername, "unknown");
    }

    /**
     * 登出 (注销 SIP)
     */
    public void logout() {
        log.info("开始登出");

        // 1. 发布离线状态
        if (registered) {
            publishStatus("offline");
        }

        // 2. 注销 SIP
        registerManager.unregister();

        // 3. 清空缓存
        friendStatusMap.clear();
        currentUsername = null;
        currentPassword = null;  // 清空密码
        registered = false;

        log.info("登出完成");
    }

    /**
     * 关闭 SIP 客户端
     */
    public void shutdown() {
        log.info("关闭 SipClientManager");

        logout();
        registerManager.shutdown();

        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
        }

        initialized = false;
        log.info("SipClientManager 已关闭");
    }

    // ========== Getter/Setter ==========

    public boolean isRegistered() {
        return registered;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setFriendStatusListener(FriendStatusListener listener) {
        this.friendStatusListener = listener;
    }

    // ========== 回调接口 ==========

    /**
     * 登录回调接口
     */
    public interface LoginCallback {
        /**
         * 登录成功
         */
        void onLoginSuccess();

        /**
         * 登录失败
         *
         * @param reason 失败原因
         */
        void onLoginFailed(String reason);
    }

    /**
     * 好友状态监听接口
     */
    public interface FriendStatusListener {
        /**
         * 好友状态变更
         *
         * @param friendUsername 好友用户名
         * @param status 新状态
         */
        void onFriendStatusChanged(String friendUsername, String status);
    }
}