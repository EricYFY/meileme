package com.meileme.resort.model;

import com.meileme.resort.config.CustomerSegment;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日挂机结算报告。
 */
@Data
public class DailySettle {

    /** 游戏日（从 1 开始） */
    private int day;

    /** 当日日期展示值 */
    private String dateDisplay;

    // —— 客流链路 ——

    /** 各客群原始潜在客流 */
    private Map<String, Double> potentialBySegment = new LinkedHashMap<>();
    /** 各客群意向客流（潜在 × 转化率） */
    private Map<String, Double> intentBySegment = new LinkedHashMap<>();
    /** 各客群实际接待人数（床位容量截断后） */
    private Map<String, Double> servedBySegment = new LinkedHashMap<>();
    /** 各客群转化率 */
    private Map<String, Double> conversionBySegment = new LinkedHashMap<>();
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
    /** 入住率（售出间数 / 住宿总间数） */
    private double occupancyRate;

    // —— 财务链路 ——
    /** 住宿总收入 */
    private double accommodationRevenue;
    /** 配套服务总收入 */
    private double supportRevenue;
    /** 当日总营收 */
    private double totalRevenue;
    /** 当日净利润 */
    private double netProfit;
    /** 结算后资金池余额 */
    private double fundsAfter;

    // —— 播报与事件 ——
    /** 结算文本事件（短缺提示、里程碑事件等） */
    private List<String> events = new ArrayList<>();
    /** 当日解锁项文本（成就解锁、地块解锁等） */
    private List<String> unlocks = new ArrayList<>();
}
