package com.meileme.resort.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 今日预计结算（纯投影）。
 *
 * <p>开新局 / 次日 0 点应用指令后刷新一次，仅供前端「今日预计」面板展示；
 * 不扣减资金池、不修改累计统计，与真实日结资金变动解耦。</p>
 */
@Data
public class DailyPreview {

    /** 各客群原始潜在客流 */
    private Map<String, Double> potentialBySegment = new LinkedHashMap<>();

    /** 各客群吸引力得分 */
    private Map<String, Double> attractionBySegment = new LinkedHashMap<>();

    /** 各客群转化率 */
    private Map<String, Double> conversionBySegment = new LinkedHashMap<>();

    /** 各客群意向客流（潜在 × 转化率） */
    private Map<String, Double> intentBySegment = new LinkedHashMap<>();

    /** 各客群实际接待人数（床位容量截断后） */
    private Map<String, Double> servedBySegment = new LinkedHashMap<>();

    /** 住宿总间数 */
    private int totalRooms;

    /** 预计售出间数 */
    private int occupiedRooms;

    /** 预计入住率（售出间数 / 总间数） */
    private double occupancyRate;

    /** 预计接待人数 */
    private double expectedGuests;

    /** 预计房屋收益 */
    private double accommodationRevenue;

    /** 预计配套收益 */
    private double supportRevenue;

    /** 预计总营收 */
    private double revenue;

    /** 预计净利润 */
    private double netProfit;
}