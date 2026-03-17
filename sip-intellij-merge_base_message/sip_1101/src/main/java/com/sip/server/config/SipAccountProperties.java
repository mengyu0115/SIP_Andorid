package com.sip.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SIP账号配置属性
 *
 * 从 application.yml 读取 sip.pre-registered-accounts 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sip")
public class SipAccountProperties {

    /**
     * SIP服务器配置
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 本地配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * 注册配置
     */
    private RegisterConfig register = new RegisterConfig();

    /**
     * 预注册账号列表
     */
    private List<AccountConfig> preRegisteredAccounts = new ArrayList<>();

    /**
     * 心跳保活配置
     */
    private KeepAliveConfig keepAlive = new KeepAliveConfig();

    @Data
    public static class ServerConfig {
        private String host = "localhost";
        private int port = 5060;
        private String domain = "minisip.local";
        private String type = "minisipserver";
    }

    @Data
    public static class LocalConfig {
        private String host = "0.0.0.0";
        private int port = 5062;
        private String transport = "udp";
    }

    @Data
    public static class RegisterConfig {
        private boolean enabled = false;
        private int expires = 3600;
        private int retryInterval = 30;
        private int maxRetry = 3;
    }

    @Data
    public static class AccountConfig {
        private String username;
        private String password;
        private String displayName;
    }

    @Data
    public static class KeepAliveConfig {
        private boolean enabled = true;
        private String method = "OPTIONS";
        private int interval = 60;
    }
}
