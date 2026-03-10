package com.sip.client.ui.conference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * 会议WebSocket客户端（支持大消息传输 - 用于屏幕共享）
 */
public class ConferenceWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceWebSocketClient.class);

    private MessageListener messageListener;

    public interface MessageListener {
        void onMessage(String message);
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public ConferenceWebSocketClient(URI serverUri) {
        super(serverUri);
        // 设置连接超时为无限（用于长时间屏幕共享）
        setConnectionLostTimeout(0);
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket连接已建立");
        if (messageListener != null) {
            messageListener.onConnected();
        }
    }

    @Override
    public void onMessage(String message) {
        logger.debug("收到WebSocket消息: {} bytes", message.length());
        if (messageListener != null) {
            messageListener.onMessage(message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket连接已关闭: code={}, reason={}, remote={}", code, reason, remote);
        if (messageListener != null) {
            messageListener.onDisconnected();
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket错误", ex);
        if (messageListener != null) {
            messageListener.onError(ex.getMessage());
        }
    }

    /**
     * 发送JSON消息
     */
    public void sendJson(String json) {
        if (isOpen()) {
            send(json);
            logger.debug("发送WebSocket消息: {} bytes", json.length());
        } else {
            logger.warn("WebSocket未连接，无法发送消息");
        }
    }
}
