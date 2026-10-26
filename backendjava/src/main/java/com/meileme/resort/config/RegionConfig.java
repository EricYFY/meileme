package com.meileme.resort.config;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 大区域配置模型（数据驱动，来源于 admin-config.json / default-config.json）。
 */
@Getter
@Builder
public class RegionConfig {

    private final String id;
    private final String displayName;
    private final double baseDailyFlow;
    private final Map<String, Double> segmentRatios;

    /** 本区域内指定客群的潜在客流 = 区域基础客流 × 该客群占比 */
    public double potentialOf(String segmentId) {
        if (segmentRatios == null || segmentId == null) return 0.0;
        Double ratio = segmentRatios.get(segmentId);
        return baseDailyFlow * (ratio == null ? 0.0 : ratio);
    }

    /** 本区域内指定客群的潜在客流（枚举兼容） */
    public double potentialOf(CustomerSegment segment) {
        if (segment == null) return 0.0;
        return potentialOf(segment.name());
    }
}
