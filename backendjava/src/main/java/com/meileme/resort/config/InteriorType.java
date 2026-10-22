package com.meileme.resort.config;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * 内装类型。
 *
 * <p>内装提供四类收益：<b>舒适度、承载容量、指定客群吸引力加成、所在建筑房屋收益加成</b>；
 * 全部按内装等级线性叠加，数值集中在本枚举配置。</p>
 */
@Getter
public enum InteriorType {

    /** 床位（承载型） */
    BED("床位", InteriorCategory.CARRY, 3.0, 1.0,
            affinity(CustomerSegment.FAMILY, 0.10, CustomerSegment.BUSINESS, 0.05), 0.020),
    /** 餐桌（承载型） */
    DINING_TABLE("餐桌", InteriorCategory.CARRY, 2.0, 2.0,
            affinity(CustomerSegment.FAMILY, 0.06, CustomerSegment.BUSINESS, 0.04,
                    CustomerSegment.ROMANTIC, 0.04, CustomerSegment.WELLNESS, 0.03), 0.015),
    /** 泡池（承载型） */
    HOT_SPRING_TUB("泡池", InteriorCategory.CARRY, 3.0, 1.5,
            affinity(CustomerSegment.ROMANTIC, 0.12, CustomerSegment.WELLNESS, 0.10,
                    CustomerSegment.FAMILY, 0.04), 0.030),
    /** 沙发（普通型） */
    SOFA("沙发", InteriorCategory.NORMAL, 2.0, 0.0,
            affinity(CustomerSegment.BUSINESS, 0.08, CustomerSegment.FAMILY, 0.04), 0.010),
    /** 灯具（普通型） */
    LIGHTING("灯具", InteriorCategory.NORMAL, 1.5, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.10, CustomerSegment.WELLNESS, 0.05), 0.010),
    /** 软装（普通型） */
    DECOR("软装", InteriorCategory.NORMAL, 2.5, 0.0,
            affinity(CustomerSegment.ROMANTIC, 0.08, CustomerSegment.WELLNESS, 0.06,
                    CustomerSegment.FAMILY, 0.03), 0.025);

    /** 展示名 */
    private final String displayName;
    /** 内装分类 */
    private final InteriorCategory category;
    /** 每级提供的舒适度加成 */
    private final double comfortPerLevel;
    /** 每级提供的承载容量加成（普通型为 0） */
    private final double capacityPerLevel;
    /** 每级提供的指定客群吸引力加成（未列出的客群为 0） */
    private final Map<CustomerSegment, Double> segmentAffinityPerLevel;
    /** 每级提供的所在建筑房屋收益加成比例 */
    private final double revenueBonusPerLevel;
    /** 各等级升级花费（下标 0 = 升到 Lv1，即首次安装） */
    private final double[] upgradeCostByLevel;

    InteriorType(String displayName, InteriorCategory category, double comfortPerLevel, double capacityPerLevel,
                 Map<CustomerSegment, Double> segmentAffinityPerLevel, double revenueBonusPerLevel) {
        this.displayName = displayName;
        this.category = category;
        this.comfortPerLevel = comfortPerLevel;
        this.capacityPerLevel = capacityPerLevel;
        this.segmentAffinityPerLevel = segmentAffinityPerLevel;
        this.revenueBonusPerLevel = revenueBonusPerLevel;
        this.upgradeCostByLevel = defaultCosts(name());
    }

    /** 指定客群每级吸引力加成 */
    public double segmentAffinityOf(CustomerSegment segment) {
        return segmentAffinityPerLevel.getOrDefault(segment, 0d);
    }

    /** 构造客群吸引力加成表：参数为「客群, 数值」交替 */
    private static Map<CustomerSegment, Double> affinity(Object... pairs) {
        Map<CustomerSegment, Double> map = new EnumMap<>(CustomerSegment.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((CustomerSegment) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    /** 指定等级的内装升级花费（下标 = level − 1） */
    public double upgradeCostOf(int level) {
        int idx = level - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx > upgradeCostByLevel.length - 1) {
            idx = upgradeCostByLevel.length - 1;
        }
        return upgradeCostByLevel[idx];
    }

    /** 各内装类型的等级花费表（集中配置，禁止散落硬编码） */
    private static double[] defaultCosts(String typeName) {
        switch (typeName) {
            case "BED":
                return new double[]{800, 1500, 2500};
            case "DINING_TABLE":
                return new double[]{600, 1200, 2000};
            case "HOT_SPRING_TUB":
                return new double[]{1200, 2200, 3600};
            case "SOFA":
                return new double[]{400, 800, 1400};
            case "LIGHTING":
                return new double[]{300, 600, 1000};
            case "DECOR":
            default:
                return new double[]{500, 1000, 1800};
        }
    }
}
