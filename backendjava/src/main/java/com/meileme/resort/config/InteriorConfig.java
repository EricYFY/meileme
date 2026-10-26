package com.meileme.resort.config;

import lombok.Builder;
import lombok.Getter;

import java.util.*;

/**
 * 内装配置模型（数据驱动，来源于 admin-config.json / default-config.json）。
 */
@Getter
@Builder
public class InteriorConfig {

    private final String id;
    private final String displayName;
    private final InteriorCategory category;
    private final double comfortPerLevel;
    private final double capacityPerLevel;
    private final double revenueBonusPerLevel;
    private final Map<String, Double> segmentAffinityPerLevel;
    private final double[] upgradeCostByLevel;
    private final int[] upgradeDaysByLevel;

    public Set<MotherBuildingType> getApplicableMothers() {
        return Collections.emptySet();
    }

    /** 指定等级的内装升级花费（下标 = level − 1） */
    public double upgradeCostOf(int level) {
        int idx = level - 1;
        if (upgradeCostByLevel == null || upgradeCostByLevel.length == 0) {
            return 800.0 * Math.pow(2, Math.max(0, idx));
        }
        if (idx < 0) return 0;
        if (idx < upgradeCostByLevel.length) return upgradeCostByLevel[idx];
        return upgradeCostByLevel[upgradeCostByLevel.length - 1] * Math.pow(2, idx - upgradeCostByLevel.length + 1);
    }

    /** 指定等级的内装升级工期（游戏日，下标 = level − 1） */
    public int upgradeDaysOf(int level) {
        int idx = level - 1;
        if (upgradeDaysByLevel == null || upgradeDaysByLevel.length == 0) {
            return Math.max(1, level);
        }
        if (idx < 0) return 0;
        if (idx < upgradeDaysByLevel.length) return upgradeDaysByLevel[idx];
        return upgradeDaysByLevel[upgradeDaysByLevel.length - 1];
    }

    /** 指定客群每级吸引力加成 */
    public double segmentAffinityOf(String segmentId) {
        if (segmentAffinityPerLevel == null || segmentId == null) return 0.0;
        return segmentAffinityPerLevel.getOrDefault(segmentId, 0.0);
    }

    /** 指定客群每级吸引力加成（枚举兼容） */
    public double segmentAffinityOf(CustomerSegment segment) {
        if (segment == null) return 0.0;
        return segmentAffinityOf(segment.name());
    }
}
