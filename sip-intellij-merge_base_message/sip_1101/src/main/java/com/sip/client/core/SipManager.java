package com.sip.client.core;

import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SIP 核心管理器 (单例)
 * 负责 SIP 协议栈的生命周期管理和资源分配
 *
 * 核心功能:
 * 1. 初始化 SIP 协议栈 (SipStack)
 * 2. 创建 SIP 提供者 (SipProvider)
 * 3. 创建工厂类 (AddressFactory, HeaderFactory, MessageFactory)
 * 4. 管理监听点 (ListeningPoint)
 * 5. 关闭和清理资源
 *
 * 设计模式: 单例模式
 * 作用: 作为所有 SIP 功能模块的基础设施层
 *
 * @author SIP 项目组
 * @version 1.0
 */
@Slf4j
public class SipManager {

    // ========== 单例模式 ==========
    private static volatile SipManager instance;

    public static SipManager getInstance() {
        if (instance == null) {
            synchronized (SipManager.class) {
                if (instance == null) {
                    instance = new SipManager();
                }
            }
        }
        return instance;
    }

    // ========== SIP 核心组件 ==========
    private SipStack sipStack;
    private SipProvider sipProvider;
    private ListeningPoint listeningPoint;

    // ========== SIP 工厂类 ==========
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    // ========== SIP 配置参数 ==========
    private String localIp;
    private int localPort = 5060;  // 默认 SIP 端口
    private String transport = "udp";  // 默认传输协议

    // ========== 状态管理 ==========
    private AtomicBoolean initialized = new AtomicBoolean(false);

    private SipManager() {
        // 私有构造函数，防止外部实例化
    }

    /**
     * 初始化 SIP 协议栈
     *
     * @param localIp 本地 IP 地址
     * @param localPort 本地端口
     * @throws Exception 初始化失败时抛出异常
     */
    public void initialize(String localIp, int localPort) throws Exception {
        initialize(localIp, localPort, "udp");
    }

    /**
     * 初始化 SIP 协议栈（完整版）
     *
     * @param localIp 本地 IP 地址
     * @param localPort 本地端口
     * @param transport 传输协议 (udp/tcp/tls)
     * @throws Exception 初始化失败时抛出异常
     */
    public void initialize(String localIp, int localPort, String transport) throws Exception {
        if (initialized.get()) {
            log.warn("SipManager 已经初始化，跳过重复初始化");
            return;
        }

        log.info("========================================");
        log.info("开始初始化 SIP 核心管理器");
        log.info("本地地址: {}:{}", localIp, localPort);
        log.info("传输协议: {}", transport.toUpperCase());
        log.info("========================================");

        this.localIp = localIp;
        this.localPort = localPort;
        this.transport = transport.toLowerCase();

        try {
            // 1. 获取 SIP 工厂实例
            SipFactory sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            log.info("步骤 1/5: SipFactory 创建成功");

            // 2. 配置 SIP 协议栈属性
            Properties properties = createSipStackProperties();

            // 3. 创建 SIP 协议栈
            sipStack = sipFactory.createSipStack(properties);
            log.info("步骤 2/5: SipStack 创建成功");

            // 4. 创建工厂类
            addressFactory = sipFactory.createAddressFactory();
            headerFactory = sipFactory.createHeaderFactory();
            messageFactory = sipFactory.createMessageFactory();
            log.info("步骤 3/5: 工厂类创建成功 (AddressFactory, HeaderFactory, MessageFactory)");

            // 5. 创建监听点
            listeningPoint = sipStack.createListeningPoint(localIp, localPort, this.transport);
            log.info("步骤 4/5: ListeningPoint 创建成功 ({}:{}:{})", localIp, localPort, this.transport);

            // 6. 创建 SIP 提供者
            sipProvider = sipStack.createSipProvider(listeningPoint);
            log.info("步骤 5/5: SipProvider 创建成功");

            // 标记为已初始化
            initialized.set(true);

            log.info("========================================");
            log.info("SIP 核心管理器初始化完成！");
            log.info("========================================");

        } catch (Exception e) {
            log.error("SIP 核心管理器初始化失败", e);
            shutdown();  // 清理可能创建的资源
            throw e;
        }
    }

    /**
     * 创建 SIP 协议栈配置属性
     *
     * @return Properties 配置对象
     */
    private Properties createSipStackProperties() {
        Properties properties = new Properties();

        // 基本配置
        properties.setProperty("javax.sip.STACK_NAME", "SIP-IM-System");
        properties.setProperty("javax.sip.IP_ADDRESS", localIp);

        // NIST SIP 实现的特定配置
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");  // 日志级别
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "logs/sip_debug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "logs/sip_server.txt");

        // 性能优化配置
        properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "8");  // 线程池大小
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");  // 允许重入
        properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "true");  // 缓存连接

        // 消息处理配置
        properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "10000");  // 最大消息大小
        properties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");  // 读超时

        log.debug("SIP 协议栈配置: {}", properties);

        return properties;
    }

    /**
     * 创建额外的 SIP 提供者 (支持多端口)
     *
     * @param port 端口号
     * @param transport 传输协议
     * @return SipProvider
     * @throws Exception 创建失败时抛出异常
     */
    public SipProvider createSipProvider(int port, String transport) throws Exception {
        if (!initialized.get()) {
            throw new IllegalStateException("SipManager 未初始化，请先调用 initialize()");
        }

        log.info("创建额外的 SipProvider: {}:{}:{}", localIp, port, transport);

        ListeningPoint newListeningPoint = sipStack.createListeningPoint(localIp, port, transport);
        SipProvider newProvider = sipStack.createSipProvider(newListeningPoint);

        log.info("新 SipProvider 创建成功");

        return newProvider;
    }

    /**
     * 关闭 SIP 管理器，释放所有资源
     */
    public void shutdown() {
        if (!initialized.get()) {
            log.warn("SipManager 未初始化，无需关闭");
            return;
        }

        log.info("========================================");
        log.info("开始关闭 SIP 核心管理器");
        log.info("========================================");

        try {
            // 1. 删除 SIP 提供者
            if (sipProvider != null && listeningPoint != null) {
                sipProvider.removeListeningPoint(listeningPoint);
                sipStack.deleteSipProvider(sipProvider);
                log.info("步骤 1/3: SipProvider 已删除");
            }

            // 2. 删除监听点
            if (sipStack != null && listeningPoint != null) {
                sipStack.deleteListeningPoint(listeningPoint);
                log.info("步骤 2/3: ListeningPoint 已删除");
            }

            // 3. 停止 SIP 协议栈
            if (sipStack != null) {
                sipStack.stop();
                log.info("步骤 3/3: SipStack 已停止");
            }

            // 4. 清空引用
            sipProvider = null;
            listeningPoint = null;
            sipStack = null;
            addressFactory = null;
            headerFactory = null;
            messageFactory = null;

            initialized.set(false);

            log.info("========================================");
            log.info("SIP 核心管理器已关闭");
            log.info("========================================");

        } catch (Exception e) {
            log.error("关闭 SIP 核心管理器时发生错误", e);
        }
    }

    // ========== Getter 方法 ==========

    /**
     * 获取 SIP 协议栈
     *
     * @return SipStack
     * @throws IllegalStateException 如果未初始化
     */
    public SipStack getSipStack() {
        checkInitialized();
        return sipStack;
    }

    /**
     * 获取 SIP 提供者
     *
     * @return SipProvider
     * @throws IllegalStateException 如果未初始化
     */
    public SipProvider getSipProvider() {
        checkInitialized();
        return sipProvider;
    }

    /**
     * 获取地址工厂
     *
     * @return AddressFactory
     * @throws IllegalStateException 如果未初始化
     */
    public AddressFactory getAddressFactory() {
        checkInitialized();
        return addressFactory;
    }

    /**
     * 获取头部工厂
     *
     * @return HeaderFactory
     * @throws IllegalStateException 如果未初始化
     */
    public HeaderFactory getHeaderFactory() {
        checkInitialized();
        return headerFactory;
    }

    /**
     * 获取消息工厂
     *
     * @return MessageFactory
     * @throws IllegalStateException 如果未初始化
     */
    public MessageFactory getMessageFactory() {
        checkInitialized();
        return messageFactory;
    }

    /**
     * 获取监听点
     *
     * @return ListeningPoint
     * @throws IllegalStateException 如果未初始化
     */
    public ListeningPoint getListeningPoint() {
        checkInitialized();
        return listeningPoint;
    }

    /**
     * 获取本地 IP
     *
     * @return 本地 IP 地址
     */
    public String getLocalIp() {
        return localIp;
    }

    /**
     * 获取本地端口
     *
     * @return 本地端口号
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * 获取传输协议
     *
     * @return 传输协议 (udp/tcp/tls)
     */
    public String getTransport() {
        return transport;
    }

    /**
     * 检查是否已初始化
     *
     * @return true 如果已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 检查是否已初始化（私有方法，抛出异常）
     *
     * @throws IllegalStateException 如果未初始化
     */
    private void checkInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException(
                "SipManager 未初始化，请先调用 initialize() 方法"
            );
        }
    }

    /**
     * 获取 SIP 栈信息（用于调试）
     *
     * @return 栈信息字符串
     */
    public String getStackInfo() {
        if (!initialized.get()) {
            return "SipManager 未初始化";
        }

        return String.format(
            "SIP Stack Info:\n" +
            "  - 本地地址: %s:%d\n" +
            "  - 传输协议: %s\n" +
            "  - Stack Name: %s\n" +
            "  - 已初始化: %s",
            localIp, localPort, transport,
            sipStack != null ? sipStack.getStackName() : "N/A",
            initialized.get()
        );
    }
}
