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
        System.out.println("Client connected: " + session.getId());

        // ★ 若后端引擎正在运行，立即向新连接的客户端推送全量运行状态与地图！
        if (gameEngineService != null && gameEngineService.isRunning()) {
            if (cachedMapMessage != null) {
                try {
                    session.sendMessage(new TextMessage(cachedMapMessage));
                    System.out.println("<<< [自动同步] 向新连接客户端发送 MAP_DATA: " + session.getId());
                } catch (Exception e) {}
            }
            gameEngineService.syncStateToSession(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("Client disconnected: " + session.getId());
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.meileme.backend.service.GameEngineService gameEngineService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println(">>> [来自 Client] 收到指令: " + payload);
        
        try {
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("command")) {
                String cmd = jsonNode.get("command").asText();
                if ("GET_MAP".equals(cmd)) {
                    if (cachedMapMessage != null) {
                        session.sendMessage(new TextMessage(cachedMapMessage));
                        System.out.println("<<< [发给 Client] 返回地图数据 (MAP_DATA) 给客户端: " + session.getId());
                    } else {
                        session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Map not loaded yet\"}"));
                        System.out.println("<<< [发给 Client] 报错：地图尚未加载");
                    }
                } else if ("CHECK_STATUS".equals(cmd) || "GET_STATUS".equals(cmd)) {
                    if (gameEngineService != null && gameEngineService.isRunning()) {
                        if (cachedMapMessage != null) {
                            session.sendMessage(new TextMessage(cachedMapMessage));
                        }
                        gameEngineService.syncStateToSession(session);
                    } else {
                        session.sendMessage(new TextMessage("{\"type\":\"SIMULATION_STOPPED\"}"));
                    }
                } else if ("START_SIMULATION".equals(cmd)) {
                    int mCount = jsonNode.has("merchantCount") ? jsonNode.get("merchantCount").asInt() : 5;
                    int rCount = jsonNode.has("riderCount") ? jsonNode.get("riderCount").asInt() : 10;
                    String mapId = jsonNode.has("mapId") ? jsonNode.get("mapId").asText() : null;
                    System.out.println(">>> [来自 Client] 请求启动引擎，商家数: " + mCount + ", 骑手数: " + rCount + ", 地图ID: " + mapId);
                    gameEngineService.startSimulation(mCount, rCount, mapId);
                } else if ("PAUSE_SIMULATION".equals(cmd)) {
                    System.out.println(">>> [来自 Client] 请求暂停模拟");
                    gameEngineService.pauseSimulation();
                } else if ("RESUME_SIMULATION".equals(cmd)) {
                    System.out.println(">>> [来自 Client] 请求继续模拟");
                    gameEngineService.resumeSimulation();
                } else if ("STOP_SIMULATION".equals(cmd)) {
                    System.out.println(">>> [来自 Client] 请求结束模拟");
                    gameEngineService.stopSimulation();
                } else if ("SET_SIMULATION_SPEED".equals(cmd)) {
                    double speed = jsonNode.has("speed") ? jsonNode.get("speed").asDouble() : 1.0;
                    System.out.println(">>> [来自 Client] 切换全局倍速: " + speed + "x");
                    gameEngineService.setSimulationSpeed(speed);
                } else if ("UPDATE_FINANCIAL_CONFIG".equals(cmd)) {
                    double takeRate = jsonNode.has("platformTakeRate") ? jsonNode.get("platformTakeRate").asDouble() : 0.25;
                    double bonusMin = jsonNode.has("riderBonusMin") ? jsonNode.get("riderBonusMin").asDouble() : 3.0;
                    double bonusMax = jsonNode.has("riderBonusMax") ? jsonNode.get("riderBonusMax").asDouble() : 8.0;
                    System.out.println(String.format(">>> [来自 Client] 更新财务费率参数: 抽成比例=%.2f, 提成区间=[%.1f, %.1f]", takeRate, bonusMin, bonusMax));
                    gameEngineService.updateFinancialConfig(takeRate, bonusMin, bonusMax);
                } else if ("UPDATE_MERCHANT_CONFIG".equals(cmd)) {
                    String mId = jsonNode.has("merchantId") ? jsonNode.get("merchantId").asText() : "";
                    double minPrice = jsonNode.has("minOrderPrice") ? jsonNode.get("minOrderPrice").asDouble() : 15.0;
                    double maxPrice = jsonNode.has("maxOrderPrice") ? jsonNode.get("maxOrderPrice").asDouble() : 35.0;
                    double matRate = jsonNode.has("materialCostRate") ? jsonNode.get("materialCostRate").asDouble() : 0.50;
                    double rent = jsonNode.has("rentCost") ? jsonNode.get("rentCost").asDouble() : 100.0;
                    double labor = jsonNode.has("laborCost") ? jsonNode.get("laborCost").asDouble() : 150.0;
                    double adSpend = jsonNode.has("adSpend") ? jsonNode.get("adSpend").asDouble() : 20.0;
                    double franchise = jsonNode.has("dailyFranchiseFee") ? jsonNode.get("dailyFranchiseFee").asDouble() : 50.0;
                    
                    System.out.println(String.format(">>> [来自 Client] 更新商家经营参数: ID=%s, 客单价=[%.1f, %.1f], 耗材=%.1f%%, 租金=%.1f, 人工=%.1f, 广告=%.1f",
                            mId, minPrice, maxPrice, matRate * 100, rent, labor, adSpend));
                    gameEngineService.updateMerchantConfig(mId, minPrice, maxPrice, matRate, rent, labor, adSpend, franchise);
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
