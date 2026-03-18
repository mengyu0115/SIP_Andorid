package com.sip.client.ui.service;

import com.sip.client.config.SipConfig;
import com.sip.client.register.SipRegisterManager;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * SIP客户端服务类
 * 使用 SipRegisterManager 处理SIP注册、认证等功能
 */
@Slf4j
public class SipClientService {

    // MiniSIPServer配置 - 从 application.yml 读取
    private static final String SIP_SERVER_HOST = SipConfig.getSipServerHost();
    private static final int SIP_SERVER_PORT = SipConfig.getSipServerPort();
    private static final String SIP_DOMAIN = SipConfig.getSipDomain();

    private SipRegisterManager registerManager;
    private String localIp;
    private int localPort;
    private String username;
    private String password;
    private boolean registered = false;

    private LoginCallback loginCallback;
    private MessageCallback messageCallback;

    // 静态变量用于追踪端口使用
    private static int nextAvailablePort = 5081;

    public interface LoginCallback {
        void onSuccess();
        void onFailure(String reason);
    }

    public interface MessageCallback {
        /**
         * 收到会议邀请
         * @param inviter 邀请人
         * @param conferenceId 会议ID
         * @param conferenceTitle 会议标题
         */
        void onConferenceInvite(String inviter, String conferenceId, String conferenceTitle);
    }

    /**
     * 登录（注册到SIP服务器）
     */
    public boolean login(String username, String password) throws Exception {
        this.username = username;
        this.password = password;

        // 智能获取与SIP服务器在同一网段的本地IP
        localIp = getPreferredLocalIp();

        // 尝试找到可用端口
        localPort = findAvailablePort();

        log.info("使用本地地址: {}:{}", localIp, localPort);

        // 使用 CountDownLatch 等待注册结果
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        // 初始化注册管理器
        registerManager = SipRegisterManager.getInstance();
        registerManager.initialize(localIp, localPort);

        // 设置回调
        registerManager.setCallback(new SipRegisterManager.RegisterCallback() {
            @Override
            public void onRegisterSuccess() {
                log.info("✓✓✓ 注册成功! ✓✓✓");
                registered = true;
                success[0] = true;
                latch.countDown();

                if (loginCallback != null) {
                    loginCallback.onSuccess();
                }
            }

            @Override
            public void onRegisterFailed(String reason) {
                log.error("注册失败: {}", reason);
                registered = false;
                success[0] = false;
                latch.countDown();

                if (loginCallback != null) {
                    loginCallback.onFailure(reason);
                }
            }

            @Override
            public void onUnregisterSuccess() {
                log.info("注销成功");
                registered = false;
            }

            @Override
            public void onMessageReceived(String from, String messageBody) {
                log.info("📨 收到SIP消息: from={}, body={}", from, messageBody);
                log.info("📨 当前messageCallback状态: {}", messageCallback != null ? "已设置" : "未设置");

                // 解析JSON消息
                try {
                    // 简单的JSON解析（手动解析，避免引入额外依赖）
                    if (messageBody.contains("\"type\":\"conference_invite\"")) {
                        String conferenceId = extractJsonValue(messageBody, "conferenceId");
                        String conferenceTitle = extractJsonValue(messageBody, "conferenceTitle");
                        String inviter = extractJsonValue(messageBody, "inviter");

                        log.info("🎉 收到会议邀请: 来自={}, 会议ID={}, 标题={}", inviter, conferenceId, conferenceTitle);

                        // 通知UI层
                        if (messageCallback != null) {
                            log.info("✅ 准备调用messageCallback.onConferenceInvite()");
                            messageCallback.onConferenceInvite(inviter, conferenceId, conferenceTitle);
                            log.info("✅ messageCallback.onConferenceInvite() 调用完成");
                        } else {
                            log.error("❌ messageCallback 为 null，无法显示邀请对话框！");
                        }
                    } else {
                        log.info("📨 收到的消息不是会议邀请类型");
                    }
                } catch (Exception e) {
                    log.error("解析消息失败", e);
                }
            }
        });

        // 执行注册
        registerManager.register(username, password, SIP_DOMAIN, SIP_SERVER_HOST, SIP_SERVER_PORT);

        // 等待注册结果（最多5秒）
        boolean timeout = !latch.await(5, TimeUnit.SECONDS);

        if (timeout) {
            log.warn("注册超时");
            if (loginCallback != null) {
                loginCallback.onFailure("注册超时");
            }
            return false;
        }

        return success[0];
    }

    /**
     * 查找可用端口
     */
    private int findAvailablePort() {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            int port = nextAvailablePort++;
            if (isPortAvailable(port)) {
                return port;
            }
        }
        // 如果都失败，尝试随机端口
        return nextAvailablePort++;
    }

    /**
     * 检查端口是否可用
     */
    private boolean isPortAvailable(int port) {
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 智能获取本地IP地址
     * 优先选择与SIP服务器在同一网段的IP，避免获取到虚拟网卡IP
     */
    private String getPreferredLocalIp() {
        try {
            // 首先尝试获取与SIP服务器在同一网段的IP
            String serverIp = SIP_SERVER_HOST;
            String serverPrefix = serverIp.substring(0, serverIp.lastIndexOf('.'));

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 跳过未启用或虚拟网卡
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // 只处理IPv4地址
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();

                        // 优先返回与服务器同网段的IP
                        if (ip.startsWith(serverPrefix)) {
                            log.info("找到与SIP服务器同网段的本地IP: {}", ip);
                            return ip;
                        }
                    }
                }
            }

            // 如果没找到同网段的IP，返回第一个非环回的IPv4地址
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        log.info("使用本地IP: {}", ip);
                        return ip;
                    }
                }
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
            log.error("获取默认IP失败: {}", e.getMessage());
            return "127.0.0.1";
        }
    }

    /**
     * 注销登录
     */
    public void logout() {
        shutdown();
    }

    /**
     * 关闭SIP服务
     */
    public void shutdown() {
        try {
            if (registerManager != null && registered) {
                log.info("开始注销...");
                registerManager.unregister();
                Thread.sleep(500); // 等待注销完成
            }

            // 关闭注册管理器
            if (registerManager != null) {
                registerManager.shutdown();
            }

            registered = false;
            log.info("SIP栈已关闭，资源已释放");

        } catch (Exception e) {
            log.error("注销失败", e);
        }
    }

    /**
     * 发起呼叫
     * TODO: 实现呼叫功能
     */
    public void makeCall(String targetUri) {
        log.warn("makeCall功能待实现: {}", targetUri);
        // 这个功能需要在后续实现
        // 可以使用registerManager.getSipProvider()等方法来发送INVITE请求
    }

    /**
     * 发送会议邀请消息
     *
     * @param targetUsername 目标用户名（如: 102）
     * @param conferenceId 会议ID
     * @param conferenceTitle 会议标题
     * @return 是否发送成功
     */
    public boolean sendConferenceInvite(String targetUsername, String conferenceId, String conferenceTitle) {
        try {
            log.info("====================================================");
            log.info("📤 准备发送会议邀请");
            log.info("📤 目标用户: {}", targetUsername);
            log.info("📤 会议ID: {}", conferenceId);
            log.info("📤 会议标题: {}", conferenceTitle);
            log.info("====================================================");

            if (registerManager == null || !registered) {
                log.error("❌ 无法发送邀请: SIP未注册 (registerManager={}, registered={})",
                    registerManager != null, registered);
                return false;
            }

            log.info("✅ SIP已注册，准备构建MESSAGE请求");

            // 构建目标URI: sip:102@myvoipapp.com
            String targetUri = "sip:" + targetUsername + "@" + SIP_DOMAIN;
            log.info("📤 目标URI: {}", targetUri);

            // 构建邀请消息内容（JSON格式）
            String messageBody = String.format(
                "{\"type\":\"conference_invite\",\"conferenceId\":\"%s\",\"conferenceTitle\":\"%s\",\"inviter\":\"%s\"}",
                conferenceId, conferenceTitle, username
            );
            log.info("📤 消息内容: {}", messageBody);

            // 获取 SIP 工厂对象
            SipProvider sipProvider = registerManager.getSipProvider();
            AddressFactory addressFactory = registerManager.getAddressFactory();
            HeaderFactory headerFactory = registerManager.getHeaderFactory();
            MessageFactory messageFactory = registerManager.getMessageFactory();

            // 创建 Request URI
            javax.sip.address.URI requestURI = addressFactory.createURI(targetUri);

            // 创建 From Header
            String fromUri = "sip:" + username + "@" + SIP_DOMAIN;
            javax.sip.address.Address fromAddress = addressFactory.createAddress(fromUri);
            fromAddress.setDisplayName(username);
            javax.sip.header.FromHeader fromHeader = headerFactory.createFromHeader(
                fromAddress,
                Integer.toString((int) (Math.random() * 10000))
            );

            // 创建 To Header
            javax.sip.address.Address toAddress = addressFactory.createAddress(targetUri);
            toAddress.setDisplayName(targetUsername);
            javax.sip.header.ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            // 创建 Via Header (使用智能获取的本地IP，支持双机测试)
            javax.sip.header.ViaHeader viaHeader = headerFactory.createViaHeader(
                this.localIp,  // 使用成员变量中已通过getPreferredLocalIp()智能获取的IP
                localPort,
                "udp",
                null
            );

            // 创建 Call-ID
            javax.sip.header.CallIdHeader callIdHeader = sipProvider.getNewCallId();

            // 创建 CSeq
            javax.sip.header.CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "MESSAGE");

            // 创建 Max-Forwards
            javax.sip.header.MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            // 创建 MESSAGE 请求
            javax.sip.message.Request request = messageFactory.createRequest(
                requestURI,
                "MESSAGE",
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                java.util.Collections.singletonList(viaHeader),
                maxForwards
            );

            // 添加 Content-Type header
            javax.sip.header.ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(
                "application", "json"
            );
            request.setContent(messageBody, contentTypeHeader);

            // 发送请求
            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();

            log.info("====================================================");
            log.info("✅ 会议邀请MESSAGE已发送");
            log.info("✅ 发送者: {} -> 接收者: {}", username, targetUsername);
            log.info("====================================================");
            return true;

        } catch (Exception e) {
            log.error("====================================================");
            log.error("❌ 发送会议邀请失败", e);
            log.error("====================================================");
            return false;
        }
    }

    /**
     * 获取SIP工厂对象（用于其他SIP操作）
     */
    public SipProvider getSipProvider() {
        return registerManager != null ? registerManager.getSipProvider() : null;
    }

    public AddressFactory getAddressFactory() {
        return registerManager != null ? registerManager.getAddressFactory() : null;
    }

    public HeaderFactory getHeaderFactory() {
        return registerManager != null ? registerManager.getHeaderFactory() : null;
    }

    public MessageFactory getMessageFactory() {
        return registerManager != null ? registerManager.getMessageFactory() : null;
    }

    public boolean isRegistered() {
        return registered;
    }

    public String getUsername() {
        return username;
    }

    /**
     * 获取密码（用于认证）
     */
    public String getPassword() {
        return password;
    }

    public void setLoginCallback(LoginCallback callback) {
        this.loginCallback = callback;
    }

    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
        log.info("📞 MessageCallback已设置: {}", callback != null ? "成功" : "失败");
    }

    /**
     * 简单的JSON值提取工具（避免引入额外JSON库依赖）
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
     * 获取 SipRegisterManager 实例
     * @return SipRegisterManager
     */
    public SipRegisterManager getRegisterManager() {
        return registerManager;
    }
}
