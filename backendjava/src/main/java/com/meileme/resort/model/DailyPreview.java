package com.meileme.resort.model;

import lombok.Data;

/**
 * 今日预计结算（纯投影）。
 *
 * <p>开新局 / 次日 0 点应用指令后刷新一次，仅供前端「今日预计」面板展示；
 * 不扣减资金池、不修改累计统计，与真实日结资金变动解耦。</p>
 */
@Data
public class DailyPreview {

    /** 住宿总间数 */
    private int totalRooms;

    /** 预计售出间数 */
    private int occupiedRooms;

    /** 预计入住率（售出间数 / 总间数） */
    private double occupancyRate;

    /** 预计接待人数 */
    private double expectedGuests;

    /** 预计住宿收入 */
    private double accommodationRevenue;

    /** 预计配套收入 */
    private double supportRevenue;

    /** 预计总收入 */
    private double revenue;

    /** 预计建筑维护费 */
    private double maintenance;

    /** 预计员工薪资 */
    private double salary;

    /** 预计总支出 */
    private double expense;

    /** 预计净利润 */
    private double netProfit;
}