package com.meileme.resort.model;

import com.meileme.resort.config.CustomerSegment;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当日客流分配结果（住宿间数售出 + 配套服务人次），供财务结算使用。
 */
@Data
public class DemandResult {

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

    /** 单建筑售出间数（住宿） */
    private Map<String, Integer> soldRoomsByBuilding = new HashMap<>();
    /** 单建筑当日服务人次（配套） */
    private Map<String, Double> supportServiceByBuilding = new HashMap<>();

    /** 潜在客流合计 */
    private double totalPotential;
    /** 意向客流合计 */
    private double totalIntent;
    /** 实际接待合计 */
    private double totalServed;
    /** 溢出流失客流 */
    private double overflowGuests;

    /** 住宿总间数 */
    private int totalRooms;
    /** 售出间数 */
    private int occupiedRooms;

    public double getServedOf(String segmentId) {
        return servedBySegment.getOrDefault(segmentId, 0.0);
    }

    public double getServedOf(CustomerSegment seg) {
        return seg == null ? 0.0 : getServedOf(seg.name());
    }
}
