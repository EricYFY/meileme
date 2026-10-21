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
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** WebSocket 网关：接收玩家操作指令、下发游戏状态与报告。 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArrayList<WebSocketSession> SESSIONS = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Lazy
    private GameEngine gameEngine;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.add(session);
        System.out.println("Client connected: " + session.getId());
        if (gameEngine != null) {
            gameEngine.syncStateToSession(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session);
        System.out.println("Client disconnected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
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
                    gameEngine.setSimulationSpeed(node.has("speed") ? node.get("speed").asDouble() : 1.0);
                    break;
                case "BUILD_BUILDING": {
                    MotherBuildingType type = MotherBuildingType.valueOf(node.get("buildingType").asText());
                    String plotId = node.has("plotId") ? node.get("plotId").asText() : "A_COUNTRYSIDE";
                    gameEngine.requestBuild(session, type, plotId);
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
                    InteriorType type = InteriorType.valueOf(node.get("interiorType").asText());
                    gameEngine.requestInteriorUpgrade(session,
                            node.has("buildingId") ? node.get("buildingId").asText() : "", type);
                    break;
                }
                case "HIRE_STAFF": {
                    PositionType position = PositionType.valueOf(node.get("position").asText());
                    int level = node.has("level") ? node.get("level").asInt() : 1;
                    gameEngine.requestHire(session, position, level);
                    break;
                }
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
            }
        } catch (Exception e) {
            System.err.println("解析客户端消息失败: " + e.getMessage());
        }
    }

    /** 向所有客户端广播 */
    public void broadcastMessage(String message) {
        for (WebSocketSession session : SESSIONS) {
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
        } catch (IOException e) {
            System.err.println("发送失败: " + e.getMessage());
        }
    }
}
