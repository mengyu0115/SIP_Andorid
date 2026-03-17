package com.sip.server.config;

import com.sip.client.core.SipManager;
import com.sip.client.media.MediaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetAddress;

/**
 * SIP 核心模块初始化配置
 *
 * 负责在Spring Boot启动时初始化SIP和媒体核心模块
 * - SipManager: SIP协议栈管理器 (队友的core模块)
 * - MediaManager: 音视频媒体管理器 (队友的media模块)
 *
 * @author SIP Team - Member 4
 * @version 1.0 - 集成核心模块
 */
@Slf4j
@Configuration
public class SipCoreConfig {

    @Value("${sip.local.port:5060}")
    private int sipLocalPort;

    @Value("${sip.transport:udp}")
    private String sipTransport;

    @Value("${rtp.audio.port-start:11000}")
    private int audioPortStart;

    @Value("${rtp.video.port-start:20001}")
    private int videoPortStart;

    /**
     * Spring Bean 初始化后自动执行
     * 注意: SIP核心模块初始化失败不会中断应用启动,以便用户注册等基础功能可以正常使用
     */
    @PostConstruct
    public void initializeCoreModules() {
        log.info("========================================");
        log.info("开始初始化 SIP 核心模块");
        log.info("========================================");

        try {
            // 1. 配置 MediaManager 端口
            MediaManager.setPortConfig(audioPortStart, videoPortStart);
            log.info("✓ MediaManager 端口配置完成 (音频:{}, 视频:{})", audioPortStart, videoPortStart);

            // 2. 初始化 SipManager (SIP协议栈)
            initializeSipManager();

            // 3. 初始化 MediaManager (音视频处理)
            initializeMediaManager();

            log.info("========================================");
            log.info("SIP 核心模块初始化完成！");
            log.info("========================================");

        } catch (Exception e) {
            log.error("========================================");
            log.error("初始化 SIP 核心模块失败 (通常是端口被占用)");
            log.error("错误信息: {}", e.getMessage());
            log.error("========================================");
            log.warn("⚠️ 应用将继续启动,但 SIP 通话功能不可用");
            log.warn("⚠️ 用户注册、登录、消息等基础功能仍可正常使用");
            log.warn("========================================");
            // 不抛出异常,允许应用继续启动
        }
    }

    /**
     * 初始化 SIP 管理器 (队友的core模块)
     */
    private void initializeSipManager() throws Exception {
        log.info("初始化 SipManager...");

        // 获取本地IP地址
        String localIp = InetAddress.getLocalHost().getHostAddress();

        SipManager sipManager = SipManager.getInstance();
        sipManager.initialize(localIp, sipLocalPort, sipTransport);

        log.info("✓ SipManager 初始化成功");
        log.info("  - 本地地址: {}:{}", localIp, sipLocalPort);
        log.info("  - 传输协议: {}", sipTransport.toUpperCase());
    }

    /**
     * 初始化媒体管理器 (队友的media模块)
     */
    private void initializeMediaManager() throws Exception {
        log.info("初始化 MediaManager...");

        MediaManager mediaManager = MediaManager.getInstance();
        mediaManager.initialize();

        log.info("✓ MediaManager 初始化成功");
        log.info("  - 本地IP: {}", mediaManager.getLocalIp());
        log.info("  - 音频端口范围: 10000+");
        log.info("  - 视频端口范围: 20000+");
    }
}
