package com.sip.server;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SIP 即时通信系统 - Spring Boot 服务端启动类
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.sip")
// 【重要】如果确认是重复扫描导致警告，请在这里删除 @MapperScan("com.sip.server.mapper")
// @MapperScan("com.sip.server.mapper")
public class ServerApplication implements ApplicationListener<WebServerInitializedEvent> { // 监听 Web 服务器初始化事件

    public static void main(String[] args) {
        log.info("========================================");
        log.info("   SIP 即时通信系统 - 后端服务启动中");
        log.info("========================================");

        SpringApplication.run(ServerApplication.class, args);
    }

    // WebServerInitializedEvent 在 Tomcat (Web Server) 启动完成后触发
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        // 1. 获取 Web 服务器实际监听的端口
        int port = event.getWebServer().getPort();

        // 2. 尝试获取本地 IP 地址，使用 Spring Environment 来获取 server.address 配置（如果存在）
        Environment env = event.getApplicationContext().getEnvironment();
        String hostAddress = "localhost";

        // 尝试获取配置中的 server.address
        String configuredAddress = env.getProperty("server.address");
        if (configuredAddress != null) {
            hostAddress = configuredAddress;
        } else {
            // 如果没有配置 server.address，则尝试获取本机 IP
            try {
                // InetAddress.getLocalHost() 可能会返回 127.0.0.1 或其他您不想要的 IP
                // 在没有 server.address 配置的情况下，要准确获取 Web Server 绑定的“外部”IP 比较困难
                // 推荐：让用户在 application.yml 中配置 server.address 或 server.host
                hostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                log.warn("无法获取本机 IP 地址, 将使用 'localhost'.", e);
                hostAddress = "localhost";
            }
        }

        // 3. 打印启动信息
        String baseUrl = "http://" + hostAddress + ":" + port;

        log.info("========================================");
        log.info("   服务启动成功!");
        log.info("   Server running at: {}", baseUrl);
        log.info("   Health Check: {}/actuator/health", baseUrl);
        log.info("   Admin Dashboard: {}/dashboard", baseUrl);
        log.info("   Conference API: {}/api/conference/list", baseUrl);
        log.info("   Admin API: {}/api/admin/monitor/summary", baseUrl);
        log.info("========================================");
    }
}