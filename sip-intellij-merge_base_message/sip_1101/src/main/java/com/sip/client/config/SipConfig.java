package com.sip.client.config;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * SIP 配置管理类 - 统一从 application.yml 读取配置
 *
 * 使用方式:
 * - String sipHost = SipConfig.getSipServerHost();
 * - int sipPort = SipConfig.getSipServerPort();
 *
 * 优点:
 * - 集中管理所有配置
 * - 换机器只需修改 application.yml 一处
 * - 避免硬编码IP地址
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
public class SipConfig {

    private static final String CONFIG_FILE = "application.yml";
    private static Map<String, Object> config;

    // 默认值（防止配置文件读取失败）
    private static final String DEFAULT_SIP_HOST = "10.129.114.129";
    private static final int DEFAULT_SIP_PORT = 5060;
    private static final String DEFAULT_SIP_DOMAIN = "myvoipapp.com";
    private static final String DEFAULT_HTTP_HOST = "localhost";
    private static final int DEFAULT_HTTP_PORT = 8081;

    static {
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try {
            log.info("正在加载配置文件: {}", CONFIG_FILE);

            Yaml yaml = new Yaml();
            InputStream inputStream = SipConfig.class.getClassLoader()
                    .getResourceAsStream(CONFIG_FILE);

            if (inputStream == null) {
                log.error("配置文件未找到: {}, 将使用默认配置", CONFIG_FILE);
                return;
            }

            config = yaml.load(inputStream);
            log.info("✅ 配置文件加载成功");
            log.info("   SIP服务器: {}:{}", getSipServerHost(), getSipServerPort());
            log.info("   HTTP服务器: {}:{}", getHttpServerHost(), getHttpServerPort());

        } catch (Exception e) {
            log.error("加载配置文件失败，将使用默认配置", e);
        }
    }

    /**
     * 从嵌套的配置Map中获取值
     */
    @SuppressWarnings("unchecked")
    private static <T> T getNestedValue(String... keys) {
        if (config == null) {
            return null;
        }

        Map<String, Object> current = config;
        for (int i = 0; i < keys.length - 1; i++) {
            Object value = current.get(keys[i]);
            if (!(value instanceof Map)) {
                return null;
            }
            current = (Map<String, Object>) value;
        }

        return (T) current.get(keys[keys.length - 1]);
    }

    // ========== SIP 服务器配置 ==========

    /**
     * 获取 SIP 服务器地址
     */
    public static String getSipServerHost() {
        String host = getNestedValue("sip", "server", "host");
        return host != null ? host : DEFAULT_SIP_HOST;
    }

    /**
     * 获取 SIP 服务器端口
     */
    public static int getSipServerPort() {
        Integer port = getNestedValue("sip", "server", "port");
        return port != null ? port : DEFAULT_SIP_PORT;
    }

    /**
     * 获取 SIP 域名
     */
    public static String getSipDomain() {
        String domain = getNestedValue("sip", "server", "domain");
        return domain != null ? domain : DEFAULT_SIP_DOMAIN;
    }

    /**
     * 获取 SIP 本地端口（基础端口，会根据用户递增）
     * ✅ 支持系统属性覆盖: -Dsip.local.port=5061
     */
    public static int getSipLocalPort() {
        // 1. 优先从系统属性读取（用于多实例测试）
        String systemPort = System.getProperty("sip.local.port");
        if (systemPort != null) {
            try {
                int port = Integer.parseInt(systemPort);
                log.debug("使用系统属性 sip.local.port = {}", port);
                return port;
            } catch (NumberFormatException e) {
                log.warn("系统属性 sip.local.port 格式错误: {}", systemPort);
            }
        }

        // 2. 从配置文件读取
        Integer port = getNestedValue("sip", "local", "port");
        return port != null ? port : 5061;
    }

    /**
     * 获取 SIP 注册过期时间（秒）
     */
    public static int getSipRegisterExpires() {
        Integer expires = getNestedValue("sip", "register", "expires");
        return expires != null ? expires : 3600;
    }

    // ========== HTTP 服务器配置 ==========

    /**
     * 获取 HTTP 服务器地址
     * （从 file.base-url 中解析，或使用 SIP 服务器地址）
     */
    public static String getHttpServerHost() {
        String baseUrl = getNestedValue("file", "base-url");
        if (baseUrl != null) {
            // 解析 http://10.129.114.129:8081/files -> 10.129.114.129
            try {
                String host = baseUrl.replace("http://", "").split(":")[0];
                return host;
            } catch (Exception e) {
                log.warn("解析 base-url 失败: {}", baseUrl, e);
            }
        }
        return getSipServerHost(); // 回退到SIP服务器地址
    }

    /**
     * 获取 HTTP 服务器端口
     */
    public static int getHttpServerPort() {
        Integer port = getNestedValue("server", "port");
        return port != null ? port : DEFAULT_HTTP_PORT;
    }

    /**
     * 获取 HTTP 服务器完整地址
     */
    public static String getHttpServerUrl() {
        return "http://" + getHttpServerHost() + ":" + getHttpServerPort();
    }

    /**
     * 获取文件服务基础URL
     */
    public static String getFileBaseUrl() {
        String baseUrl = getNestedValue("file", "base-url");
        return baseUrl != null ? baseUrl : (getHttpServerUrl() + "/files");
    }

    // ========== 会议配置 ==========

    /**
     * 获取会议最大参与人数
     */
    public static int getConferenceMaxParticipants() {
        Integer max = getNestedValue("app", "conference", "max-participants");
        return max != null ? max : 10;
    }

    /**
     * 获取会议最大时长（秒）
     */
    public static int getConferenceMaxDuration() {
        Integer duration = getNestedValue("app", "conference", "max-duration");
        return duration != null ? duration : 3600;
    }

    // ========== RTP 媒体端口配置（用于单机多实例测试）==========

    /**
     * 获取 RTP 音频端口起始值
     * ✅ 支持系统属性覆盖: -Drtp.audio.port.start=11000
     */
    public static int getRtpAudioPortStart() {
        // 1. 优先从系统属性读取
        String systemPort = System.getProperty("rtp.audio.port.start");
        if (systemPort != null) {
            try {
                int port = Integer.parseInt(systemPort);
                log.debug("使用系统属性 rtp.audio.port.start = {}", port);
                return port;
            } catch (NumberFormatException e) {
                log.warn("系统属性 rtp.audio.port.start 格式错误: {}", systemPort);
            }
        }

        // 2. 从配置文件读取
        Integer port = getNestedValue("rtp", "audio", "port-start");
        return port != null ? port : 11000;  // 默认11000
    }

    /**
     * 获取 RTP 音频端口结束值
     * ✅ 支持系统属性覆盖: -Drtp.audio.port.end=11999
     */
    public static int getRtpAudioPortEnd() {
        // 1. 优先从系统属性读取
        String systemPort = System.getProperty("rtp.audio.port.end");
        if (systemPort != null) {
            try {
                int port = Integer.parseInt(systemPort);
                log.debug("使用系统属性 rtp.audio.port.end = {}", port);
                return port;
            } catch (NumberFormatException e) {
                log.warn("系统属性 rtp.audio.port.end 格式错误: {}", systemPort);
            }
        }

        // 2. 从配置文件读取
        Integer port = getNestedValue("rtp", "audio", "port-end");
        return port != null ? port : 11999;  // 默认11999
    }

    /**
     * 获取 RTP 视频端口起始值
     * ✅ 支持系统属性覆盖: -Drtp.video.port.start=20001
     */
    public static int getRtpVideoPortStart() {
        // 1. 优先从系统属性读取
        String systemPort = System.getProperty("rtp.video.port.start");
        if (systemPort != null) {
            try {
                int port = Integer.parseInt(systemPort);
                log.debug("使用系统属性 rtp.video.port.start = {}", port);
                return port;
            } catch (NumberFormatException e) {
                log.warn("系统属性 rtp.video.port.start 格式错误: {}", systemPort);
            }
        }

        // 2. 从配置文件读取
        Integer port = getNestedValue("rtp", "video", "port-start");
        return port != null ? port : 20001;  // 默认20001
    }

    /**
     * 获取 RTP 视频端口结束值
     * ✅ 支持系统属性覆盖: -Drtp.video.port.end=21000
     */
    public static int getRtpVideoPortEnd() {
        // 1. 优先从系统属性读取
        String systemPort = System.getProperty("rtp.video.port.end");
        if (systemPort != null) {
            try {
                int port = Integer.parseInt(systemPort);
                log.debug("使用系统属性 rtp.video.port.end = {}", port);
                return port;
            } catch (NumberFormatException e) {
                log.warn("系统属性 rtp.video.port.end 格式错误: {}", systemPort);
            }
        }

        // 2. 从配置文件读取
        Integer port = getNestedValue("rtp", "video", "port-end");
        return port != null ? port : 21000;  // 默认21000
    }

    // ========== 摄像头设备配置（用于单机多实例测试）==========

    /**
     * 获取摄像头设备 ID
     * ✅ 支持系统属性覆盖: -Dcamera.device.id=0
     *
     * 单机测试场景:
     * - user100: -Dcamera.device.id=0 (第一个摄像头)
     * - user101: -Dcamera.device.id=1 (第二个摄像头，如果有的话)
     * - user102: -Dcamera.device.id=0 (可复用，但需避免同时使用)
     *
     * 注意: 如果只有一个摄像头，建议同一时刻只启动一个客户端进行视频通话
     */
    public static int getCameraDeviceId() {
        // 1. 优先从系统属性读取（用于多实例测试）
        String systemDeviceId = System.getProperty("camera.device.id");
        if (systemDeviceId != null) {
            try {
                int deviceId = Integer.parseInt(systemDeviceId);
                log.debug("使用系统属性 camera.device.id = {}", deviceId);
                return deviceId;
            } catch (NumberFormatException e) {
                log.warn("系统属性 camera.device.id 格式错误: {}", systemDeviceId);
            }
        }

        // 2. 从配置文件读取
        Integer deviceId = getNestedValue("camera", "device-id");
        return deviceId != null ? deviceId : 0;  // 默认设备0
    }

    // ========== 调试方法 ==========

    /**
     * 打印所有配置（用于调试）
     */
    public static void printConfig() {
        log.info("========== SIP 配置信息 ==========");
        log.info("SIP服务器: {}:{}", getSipServerHost(), getSipServerPort());
        log.info("SIP域名: {}", getSipDomain());
        log.info("SIP本地端口: {}", getSipLocalPort());
        log.info("SIP注册过期: {}秒", getSipRegisterExpires());
        log.info("HTTP服务器: {}", getHttpServerUrl());
        log.info("文件服务: {}", getFileBaseUrl());
        log.info("会议最大人数: {}", getConferenceMaxParticipants());
        log.info("会议最大时长: {}秒", getConferenceMaxDuration());
        log.info("==================================");
    }

    /**
     * 重新加载配置（热更新，可选功能）
     */
    public static void reload() {
        log.info("重新加载配置文件...");
        loadConfig();
    }
}
