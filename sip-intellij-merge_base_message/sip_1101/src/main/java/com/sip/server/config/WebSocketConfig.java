package com.sip.server.config;

import com.sip.server.websocket.ConferenceWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

/**
 * WebSocket 配置类
 * 支持 STOMP 协议用于 Presence 状态推送
 * 支持原始 WebSocket 用于会议信令
 *
 * @author SIP Team - Member 4
 * @version 2.0
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    @Autowired
    private ConferenceWebSocketHandler conferenceWebSocketHandler;

    @Autowired
    private com.sip.server.websocket.ConferenceMediaWebSocketHandler conferenceMediaWebSocketHandler;

    // ========== 原始 WebSocket 配置 (用于会议) ==========
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册会议信令 WebSocket 端点
        registry.addHandler(conferenceWebSocketHandler, "/ws/conference")
                .setAllowedOrigins("*"); // 允许所有来源 (生产环境应限制)

        // 注册媒体流 WebSocket 端点
        registry.addHandler(conferenceMediaWebSocketHandler, "/ws")
                .setAllowedOrigins("*"); // 允许所有来源 (生产环境应限制)
    }

    // ========== STOMP WebSocket 配置 (用于 Presence) ==========
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于广播消息
        config.enableSimpleBroker("/topic", "/queue");

        // 客户端发送消息的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点
        registry.addEndpoint("/ws-stomp")
                .setAllowedOrigins("*")  // 允许所有来源
                .withSockJS();           // 启用 SockJS 降级支持
    }

    /**
     * 配置 WebSocket 容器
     * 增加消息大小限制以支持屏幕共享等大数据传输
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置文本消息缓冲区大小为 10MB (屏幕共享Base64数据可能很大)
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        // 设置二进制消息缓冲区大小为 10MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        // 设置会话空闲超时为 30 分钟
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }
}
