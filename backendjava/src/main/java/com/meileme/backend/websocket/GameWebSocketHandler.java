package com.meileme.backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    
    // 保存所有连接的 Unity 客户端 Session
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // 缓存完整的地图 JSON 字符串，避免频繁序列化 40k 的大数组
    private String cachedMapMessage = null;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public void setCachedMapMessage(String mapMessage) {
        this.cachedMapMessage = mapMessage;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("Unity Client connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("Unity Client disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println(">>> [来自 Unity] 收到指令: " + payload);
        
        try {
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("command") && "GET_MAP".equals(jsonNode.get("command").asText())) {
                if (cachedMapMessage != null) {
                    session.sendMessage(new TextMessage(cachedMapMessage));
                    System.out.println("<<< [发给 Unity] 返回地图数据 (MAP_DATA) 给客户端: " + session.getId());
                } else {
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Map not loaded yet\"}"));
                    System.out.println("<<< [发给 Unity] 报错：地图尚未加载");
                }
            }
        } catch (Exception e) {
            System.err.println("解析客户端消息失败: " + e.getMessage());
        }
    }
    
    // 向所有客户端广播消息
    public void broadcastMessage(String message) {
        if (message.contains("\"type\":\"RIDER_UPDATE\"")) {
            // 过滤 10Hz 高频日志，如果您希望完全不打印以保持控制台整洁，可以将其注释掉
            // System.out.println("<<< [广播给 Unity] 实时同步骑手坐标 (RIDER_UPDATE)");
        } else {
            System.out.println("<<< [广播给 Unity] " + message);
        }

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
