package com.meileme.resort.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.PositionType;
import com.meileme.resort.service.GameEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** WebSocket 网关：接收玩家操作指令、下发游戏状态与报告。 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Lazy
    private GameEngine gameEngine;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession decorator = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
        SESSIONS.put(session.getId(), decorator);
        System.out.println("Client connected: " + session.getId());
        if (gameEngine != null) {
            gameEngine.syncStateToSession(decorator);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session.getId());
        System.out.println("Client disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        WebSocketSession session = SESSIONS.getOrDefault(rawSession.getId(), rawSession);
        String payload = message.getPayload();
        System.out.println(">>> [来自 Client] " + payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.has("command")) {
                return;
            }
            String cmd = node.get("command").asText();
            switch (cmd) {
                case "START_GAME":
                    gameEngine.startGame();
                    break;
                case "STOP_GAME":
                    gameEngine.stopGame();
                    break;
                case "PAUSE_GAME":
                    gameEngine.pauseGame();
                    break;
                case "RESUME_GAME":
                    gameEngine.resumeGame();
                    break;
                case "CHECK_STATUS":
                case "GET_STATUS":
                    gameEngine.syncStateToSession(session);
                    break;
                case "GET_CONFIG":
                    gameEngine.sendConfig(session);
                    break;
                case "SET_SIMULATION_SPEED":
                    gameEngine.setSimulationSpeed(session, node.has("speed") ? node.get("speed").asDouble() : 1.0);
                    break;
                case "BUILD_BUILDING": {
                    String typeStr = node.has("buildingType") ? node.get("buildingType").asText() : "";
                    MotherBuildingType type = null;
                    try {
                        type = MotherBuildingType.valueOf(typeStr);
                    } catch (Exception ignored) {
                    }
                    if (type == null) {
                        sendActionFailed(session, "BUILD_BUILDING", "BUILDING_TYPE_NOT_FOUND");
                        break;
                    }
                    String plotId = node.has("plotId") ? node.get("plotId").asText() : "STORMVEIL_CASTLE";
                    int cellX = node.has("cellX") ? node.get("cellX").asInt() : -1;
                    int cellY = node.has("cellY") ? node.get("cellY").asInt() : -1;
                    gameEngine.requestBuild(session, type, plotId, cellX, cellY);
                    break;
                }
                case "FORM_LEVEL_UP":
                    gameEngine.requestFormLevelUp(session,
                            node.has("buildingId") ? node.get("buildingId").asText() : "");
                    break;
                case "BRANCH_EVOLVE":
                    gameEngine.requestBranchEvolve(session,
                            node.has("buildingId") ? node.get("buildingId").asText() : "",
                            node.has("targetFormId") ? node.get("targetFormId").asText() : "");
                    break;
                case "MERGE_EVOLVE":
                    gameEngine.requestMergeEvolve(session,
                            node.has("buildingIdA") ? node.get("buildingIdA").asText() : "",
                            node.has("buildingIdB") ? node.get("buildingIdB").asText() : "");
                    break;
                case "INTERIOR_UPGRADE": {
                    String typeStr = node.has("interiorType") ? node.get("interiorType").asText() : "";
                    InteriorType type = null;
                    try {
                        type = InteriorType.valueOf(typeStr);
                    } catch (Exception ignored) {
                    }
                    if (type == null) {
                        sendActionFailed(session, "INTERIOR_UPGRADE", "INTERIOR_NOT_FOUND");
                        break;
                    }
                    gameEngine.requestInteriorUpgrade(session,
                            node.has("buildingId") ? node.get("buildingId").asText() : "", type);
                    break;
                }
                case "HIRE_STAFF": {
                    String posStr = node.has("position") ? node.get("position").asText() : "";
                    PositionType position = null;
                    try {
                        position = PositionType.valueOf(posStr);
                    } catch (Exception ignored) {
                    }
                    if (position == null) {
                        sendActionFailed(session, "HIRE_STAFF", "POSITION_NOT_FOUND");
                        break;
                    }
                    gameEngine.requestHire(session, position);
                    break;
                }
                case "TRAIN_STAFF":
                    gameEngine.requestTrain(session, node.has("staffId") ? node.get("staffId").asText() : "");
                    break;
                case "FIRE_STAFF":
                    gameEngine.requestFire(session, node.has("staffId") ? node.get("staffId").asText() : "");
                    break;
                case "UNLOCK_SKILL":
                    gameEngine.requestSkillUnlock(session,
                            node.has("skillId") ? node.get("skillId").asText() : "");
                    break;
                case "UNLOCK_PLOT":
                    gameEngine.requestUnlockPlot(session,
                            node.has("plotId") ? node.get("plotId").asText() : "");
                    break;
                case "REQUEST_DAILY_LOG":
                    gameEngine.sendDailyLog(session);
                    break;
                case "REQUEST_RANGE_REPORT":
                    gameEngine.sendRangeReport(session, node.has("rangeDays") ? node.get("rangeDays").asInt() : 7);
                    break;
                default:
                    System.out.println(">>> 未知指令: " + cmd);
                    sendActionFailed(session, cmd, "UNKNOWN_COMMAND");
            }
        } catch (Exception e) {
            System.err.println("解析客户端消息失败: " + e.getMessage());
        }
    }

    private void sendActionFailed(WebSocketSession session, String action, String reason) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "ACTION_FAILED",
                    "action", action,
                    "reason", reason
            ));
            sendToSession(session, json);
        } catch (Exception ignored) {
        }
    }

    /** 向所有客户端广播 */
    public void broadcastMessage(String message) {
        for (WebSocketSession session : SESSIONS.values()) {
            sendToSession(session, message);
        }
    }

    /** 向指定客户端发送 */
    public void sendToSession(WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            System.err.println("发送失败: " + e.getMessage());
        }
    }
}
