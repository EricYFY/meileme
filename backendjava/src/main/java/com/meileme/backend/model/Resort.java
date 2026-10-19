package com.meileme.backend.model;

import com.meileme.backend.config.GameConfig;
import com.meileme.backend.config.Season;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 度假村聚合根：玩家经营的全部状态。
 */
@Data
public class Resort {

    /** 局状态：实时经营中 / 已结束 */
    public enum DayPhase { RUNNING, GAME_OVER }

    // ===== 局状态 =====
    private double cash = GameConfig.INITIAL_CASH;              // 资金
    private double reputation = GameConfig.INITIAL_REPUTATION;  // 口碑 (0~100)
    private Season season = Season.SPRING;                      // 当前季节
    private int day = 1;                                        // 当前游戏日
    private long virtualTimeMs = GameConfig.BASE_VIRTUAL_TIME_MS;

    // ===== 已解锁地块 =====
    private List<String> unlockedPlots = new CopyOnWriteArrayList<>(List.of(GameConfig.PLOT_INITIAL));

    // ===== 经营资产 =====
    private List<Building> buildings = new CopyOnWriteArrayList<>();
    private List<Staff> staffList = new CopyOnWriteArrayList<>();

    // ===== 当日待决团建订单（仅当天有效，不操作次日自动拒绝） =====
    private List<GuestOrder> teamOrders = new CopyOnWriteArrayList<>();

    // ===== 待生效指令队列（次日 0 点统一生效） =====
    private List<PendingCommand> pendingCommands = new CopyOnWriteArrayList<>();

    // ===== 已接单·待生效团建订单（第 effectiveDay 天自动结算） =====
    private List<GuestOrder> scheduledOrders = new CopyOnWriteArrayList<>();

    // ===== 经营配置（玩家可调） =====
    private double standardRoomPrice = 280.0;  // 普通客房单价/晚
    private double luxuryRoomPrice   = 680.0;  // 豪华套房单价/晚
    private double adBudget = 0.0;             // 当日广告预算

    // ===== 阶段与结算 =====
    private DayPhase phase = DayPhase.RUNNING;
    private boolean gameOver = false;
    private String endReason;

    // ===== 累计统计 =====
    private int daysSurvived = 0;
    private double cumulativeProfit = 0.0;
    private int totalGuestsServed = 0;

    /** 供前端展示的季节中文名（Jackson 序列化时输出 seasonName 字段） */
    public String getSeasonName() {
        return season == null ? null : season.getLabel();
    }
}
