package com.meileme.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区间经营报告：玩家按「过去 1 天 / 7 天 / 30 天 / 365 天」主动生成。
 * 由 ResortRepository 中保留的每日 DailyReport 历史聚合而来。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RangeReport {

    private int rangeDays;   // 请求区间（游戏天）
    private int fromDay;     // 起始游戏日（含）
    private int toDay;       // 结束游戏日（含）
    private int dayCount;    // 实际覆盖天数（历史不足时小于 rangeDays）

    // ===== 资金 =====
    private double cashStart;    // 区间首日结算前资金
    private double cashEnd;      // 区间末日结算后资金
    private double netProfit;    // 区间累计净利
    private double totalRevenue; // 区间累计收入
    private double totalExpense; // 区间累计支出

    // ===== 汇总明细 =====
    private DailyReport.RevenueDetail revenueDetail;
    private DailyReport.ExpenseDetail expenseDetail;

    // ===== 经营指标 =====
    private double avgOccupancyRate; // 平均入住率 (%)
    private int totalGuestsServed;   // 累计接待游客数
    private int totalTurnedAway;     // 累计溢出未接待数

    // ===== 口碑 =====
    private double reputationStart;
    private double reputationEnd;
    private double reputationDelta;

    // ===== 事件与团建 =====
    @Builder.Default
    private Map<String, Integer> eventCounts = new LinkedHashMap<>(); // 事件名 -> 次数
    private int teamOrdersAccepted;
    private int teamOrdersDefaulted;
    private double teamOrderRevenue;
    private double teamPenalty;

    // ===== 走势 =====
    @Builder.Default
    private List<DayPoint> dailySeries = new ArrayList<>();
    private DayPoint bestDay;
    private DayPoint worstDay;

    /** 单日走势点 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayPoint {
        private int day;
        private String seasonName;
        private double netProfit;
        private double revenue;
        private double expense;
        private double occupancyRate;
        private int guestsServed;
        private double cashAfter;
        private double reputationAfter;
    }
}
