package com.sip.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{userId}")
public class NotificationWebSocket {
    private static final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        sessionMap.put(userId, session);
    }

    @OnMessage
    public void onMessage(String message) {
        // 可忽略客户端心跳
    }

    @OnClose
    public void onClose(@PathParam("userId") Long userId) {
        sessionMap.remove(userId);
    }

    public static void pushToUser(Long userId, Object data) {
        Session session = sessionMap.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(data);
                session.getAsyncRemote().sendText(json);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }
}