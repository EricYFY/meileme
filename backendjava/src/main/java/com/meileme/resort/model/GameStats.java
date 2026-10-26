package com.meileme.resort.model;

import com.meileme.resort.config.CustomerSegment;
import lombok.Data;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局累计统计（成就判定指标，跨区域累计、新区域不重置）。
 */
@Data
public class GameStats {

    /** 累计总收入 */
    private double cumulativeIncome = 0;

    /** 累计接待总人数 */
    private double totalGuests = 0;

    /** 分客群累计接待总数 */
    private Map<String, Double> segmentGuests = new LinkedHashMap<>();

    /** 历史累计新建建筑数 */
    private int buildingsBuiltCount = 0;

    /** 当前存活建筑的「形态 + 等级」计数，key = "FORM_ID_LV" */
    private Map<String, Integer> formLevelCounts = new HashMap<>();

    /** 当前在册员工的「岗位 + 等级」计数，key = "POSITION_LV" */
    private Map<String, Integer> positionLevelCounts = new HashMap<>();

    /** 当前总资产（最近一次日结后的资金池） */
    private double totalAssets = 0;

    /** 取指定客群的累计接待数 */
    public double segmentGuestsOf(String segmentId) {
        if (segmentId == null) return 0d;
        Double v = segmentGuests.get(segmentId);
        return v == null ? 0d : v;
    }

    /** 取指定客群的累计接待数（枚举兼容） */
    public double segmentGuestsOf(CustomerSegment segment) {
        return segment == null ? 0d : segmentGuestsOf(segment.name());
    }

    /** 指定客群累计接待数累加 */
    public void addSegmentGuests(String segmentId, double value) {
        if (segmentId == null || value <= 0) {
            return;
        }
        segmentGuests.merge(segmentId, value, Double::sum);
    }

    /** 指定客群累计接待数累加（枚举兼容） */
    public void addSegmentGuests(CustomerSegment segment, double value) {
        if (segment == null) return;
        addSegmentGuests(segment.name(), value);
    }

    /** 取「形态 + 等级」当前计数 */
    public int formLevelCount(String formId, int level) {
        Integer v = formLevelCounts.get(key(formId, level));
        return v == null ? 0 : v;
    }

    /** 取「岗位 + 等级」当前计数 */
    public int positionLevelCount(String positionName, int level) {
        Integer v = positionLevelCounts.get(key(positionName, level));
        return v == null ? 0 : v;
    }

    private String key(String a, int level) {
        return a + "_" + level;
    }
}
