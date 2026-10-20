package com.meileme.resort.model;

import com.meileme.resort.config.CustomerSegment;
import lombok.Data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 单日结算快照（同时用于当日结算与逐日历史，历史最多保留 365 天）。
 */
@Data
public class DailySettle {

    /** 游戏日（从 1 开始） */
    private int day;

    /** 当日日期展示值 */
    private String dateDisplay;

    // —— 客流链路 ——

    /** 各客群原始潜在客流 */
    private Map<CustomerSegment, Double> potentialBySegment = new EnumMap<>(CustomerSegment.class);
    /** 各客群意向客流（潜在 × 转化率） */
    private Map<CustomerSegment, Double> intentBySegment = new EnumMap<>(CustomerSegment.class);
    /** 各客群实际接待人数（床位容量截断后） */
    private Map<CustomerSegment, Double> servedBySegment = new EnumMap<>(CustomerSegment.class);
    /** 各客群转化率 */
    private Map<CustomerSegment, Double> conversionBySegment = new EnumMap<>(CustomerSegment.class);
    /** 潜在客流合计 */
    private double totalPotential;
    /** 意向客流合计 */
    private double totalIntent;
    /** 实际接待合计 */
    private double totalServed;
    /** 溢出流失客流（直接流失，不做二次分流） */
    private double overflowGuests;

    // —— 容量 ——
    /** 住宿总间数（存活住宿建筑数） */
    private int totalRooms;
    /** 售出间数 */
    private int occupiedRooms;
    /** 入住率（售出间数 / 总间数） */
    private double occupancyRate;

    // —— 收入与支出 ——
    /** 住宿收入 */
    private double accommodationRevenue;
    /** 配套收入 */
    private double supportRevenue;
    /** 当日总收入 */
    private double totalRevenue;
    /** 建筑每日维护费合计 */
    private double maintenanceCost;
    /** 员工每日薪资合计 */
    private double salaryCost;
    /** 当日总支出 */
    private double totalExpense;
    /** 当日净利润 */
    private double netProfit;
    /** 结算后资金池 */
    private double fundsAfter;

    // —— 事件与提示 ——
    /** 当日事件 / 提示明细（用于复盘） */
    private List<String> events = new ArrayList<>();

    /** 当日新解锁的成就 / 地块 / 技能点提示 */
    private List<String> unlocks = new ArrayList<>();
}
