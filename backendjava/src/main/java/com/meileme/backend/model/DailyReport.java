package com.meileme.backend.model;

import com.meileme.backend.config.Season;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 每日经营日报：DAILY_REPORT 的 data 载荷 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReport {

    private int day;
    private Season season;
    private String seasonName;

    // === 资金 ===
    private double cashBefore;
    private double cashAfter;
    private double netProfit;
    private double totalRevenue;
    private double totalExpense;

    // === 明细 ===
    private RevenueDetail revenueDetail;
    private ExpenseDetail expenseDetail;

    // === 经营指标 ===
    private double occupancyRate;   // 入住率 (%)
    private int guestsServed;       // 接待游客数
    private int turnedAway;         // 溢出未接待数

    // === 口碑 ===
    private double reputationBefore;
    private double reputationAfter;
    private double reputationDelta;

    // === 记录 ===
    @Builder.Default
    private List<GameEvent> events = new ArrayList<>();
    @Builder.Default
    private List<TeamOrderResult> teamOrderResults = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueDetail {
        private double roomFee;      // 散客房费收入
        private double dining;       // 餐饮消费
        private double recreation;   // 游乐消费
        private double teamOrder;    // 团建订单收入
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseDetail {
        private double staffSalary;          // 全部员工日工资
        private double buildingMaintenance;  // 建筑每日维护费
        private double adBudget;             // 广告投入
        private double eventCost;            // 事件维修费
        private double teamPenalty;          // 团建违约罚款
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamOrderResult {
        private String orderId;
        private String size;      // 规模枚举名
        private String status;    // SATISFIED / DEFAULTED
        private double revenue;
        private double penalty;
        private String reason;    // 违约原因（缺配套 / 客房不足），履约时为 null
    }
}
