package com.meileme.backend.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.backend.config.BuildingType;
import com.meileme.backend.config.StaffType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    // 保存所有连接的客户端 Session
    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // 缓存完整的地图 JSON 字符串，避免频繁序列化大数组
    private String cachedMapMessage = null;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setCachedMapMessage(String mapMessage) {
        this.cachedMapMessage = mapMessage;
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.meileme.backend.service.ResortEngineService resortEngineService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("Client connected: " + session.getId());

        // 若引擎正在运行，立即向新连接客户端推送地图与全量经营状态
        if (resortEngineService != null && resortEngineService.isRunning()) {
            if (cachedMapMessage != null) {
                try {
                    session.sendMessage(new TextMessage(cachedMapMessage));
                    System.out.println("<<< [自动同步] 向新连接客户端发送 MAP_DATA: " + session.getId());
                } catch (Exception ignored) {}
            }
            resortEngineService.syncStateToSession(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("Client disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println(">>> [来自 Client] 收到指令: " + payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (!jsonNode.has("command")) return;
            String cmd = jsonNode.get("command").asText();

            if ("GET_MAP".equals(cmd)) {
                if (cachedMapMessage != null) {
                    session.sendMessage(new TextMessage(cachedMapMessage));
                } else {
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Map not loaded yet\"}"));
                }
            } else if ("CHECK_STATUS".equals(cmd) || "GET_STATUS".equals(cmd)) {
                if (resortEngineService != null && resortEngineService.isRunning()) {
                    if (cachedMapMessage != null) session.sendMessage(new TextMessage(cachedMapMessage));
                    resortEngineService.syncStateToSession(session);
                } else {
                    session.sendMessage(new TextMessage("{\"type\":\"GAME_STOPPED\"}"));
                }
            } else if ("START_GAME".equals(cmd)) {
                String mapId = jsonNode.has("mapId") ? jsonNode.get("mapId").asText() : null;
                resortEngineService.startGame(mapId);
            } else if ("STOP_GAME".equals(cmd)) {
                resortEngineService.stopGame();
            } else if ("PAUSE_GAME".equals(cmd)) {
                resortEngineService.pauseGame();
            } else if ("RESUME_GAME".equals(cmd)) {
                resortEngineService.resumeGame();
            } else if ("SET_SIMULATION_SPEED".equals(cmd)) {
                double speed = jsonNode.has("speed") ? jsonNode.get("speed").asDouble() : 1.0;
                resortEngineService.setSimulationSpeed(speed);
            } else if ("REQUEST_RANGE_REPORT".equals(cmd)) {
                int rangeDays = jsonNode.has("rangeDays") ? jsonNode.get("rangeDays").asInt() : 7;
                resortEngineService.sendRangeReport(session, rangeDays);
            } else if ("REQUEST_DAILY_LOG".equals(cmd)) {
                resortEngineService.sendDailyLog(session);
            } else if ("BUILD_BUILDING".equals(cmd)) {
                BuildingType type = BuildingType.valueOf(jsonNode.get("buildingType").asText());
                String plotId = jsonNode.has("plotId") ? jsonNode.get("plotId").asText() : "INITIAL";
                resortEngineService.buildBuilding(type, plotId);
            } else if ("HIRE_STAFF".equals(cmd)) {
                StaffType type = StaffType.valueOf(jsonNode.get("staffType").asText());
                String salaryMode = jsonNode.has("salaryMode") ? jsonNode.get("salaryMode").asText() : "normal";
                resortEngineService.hireStaff(type, salaryMode);
            } else if ("FIRE_STAFF".equals(cmd)) {
                String staffId = jsonNode.has("staffId") ? jsonNode.get("staffId").asText() : "";
                resortEngineService.fireStaff(staffId);
            } else if ("SET_ROOM_PRICE".equals(cmd)) {
                double std = jsonNode.has("standardRoomPrice") ? jsonNode.get("standardRoomPrice").asDouble() : 0;
                double lux = jsonNode.has("luxuryRoomPrice") ? jsonNode.get("luxuryRoomPrice").asDouble() : 0;
                resortEngineService.setRoomPrice(std, lux);
            } else if ("SET_AD_BUDGET".equals(cmd)) {
                double ad = jsonNode.has("adBudget") ? jsonNode.get("adBudget").asDouble() : 0;
                resortEngineService.setAdBudget(ad);
            } else if ("ACCEPT_TEAM_ORDER".equals(cmd)) {
                String orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asText() : "";
                resortEngineService.acceptTeamOrder(orderId);
            } else if ("REJECT_TEAM_ORDER".equals(cmd)) {
                String orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asText() : "";
                resortEngineService.rejectTeamOrder(orderId);
            } else if ("UNLOCK_PLOT".equals(cmd)) {
                String plotId = jsonNode.has("plotId") ? jsonNode.get("plotId").asText() : "";
                resortEngineService.unlockPlot(plotId);
            } else {
                System.out.println(">>> [来自 Client] 未知指令: " + cmd);
            }
        } catch (Exception e) {
            System.err.println("解析客户端消息失败: " + e.getMessage());
        }
    }

    // 向所有客户端广播消息
    public void broadcastMessage(String message) {
        boolean silent = message.contains("\"type\":\"NPC_UPDATE\"")
                || message.contains("\"type\":\"TIME_TICK\"");
        if (!silent) {
            System.out.println("<<< [广播给 Client] " + message);
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
