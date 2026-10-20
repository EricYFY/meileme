package com.meileme.resort.config;

import lombok.Getter;

/**
 * 内装类型。
 *
 * <p>内装<b>不提供客群系数</b>（客群影响系数为建筑形态固有属性），仅影响两类属性：
 * 舒适度、承载容量。当前版本内装底层预留了扩展字段，暂不启用额外语义。</p>
 */
@Getter
public enum InteriorType {

    /** 床位（承载型） */
    BED("床位", InteriorCategory.CARRY, 3.0, 1.0),
    /** 餐桌（承载型） */
    DINING_TABLE("餐桌", InteriorCategory.CARRY, 2.0, 2.0),
    /** 泡池（承载型） */
    HOT_SPRING_TUB("泡池", InteriorCategory.CARRY, 3.0, 1.5),
    /** 沙发（普通型） */
    SOFA("沙发", InteriorCategory.NORMAL, 2.0, 0.0),
    /** 灯具（普通型） */
    LIGHTING("灯具", InteriorCategory.NORMAL, 1.5, 0.0),
    /** 软装（普通型） */
    DECOR("软装", InteriorCategory.NORMAL, 2.5, 0.0);

    /** 展示名 */
    private final String displayName;
    /** 内装分类 */
    private final InteriorCategory category;
    /** 每级提供的舒适度加成 */
    private final double comfortPerLevel;
    /** 每级提供的承载容量加成（普通型为 0） */
    private final double capacityPerLevel;
    /** 各等级升级花费（下标 0 = 升到 Lv1，即首次安装） */
    private final double[] upgradeCostByLevel;

    InteriorType(String displayName, InteriorCategory category, double comfortPerLevel, double capacityPerLevel) {
        this.displayName = displayName;
        this.category = category;
        this.comfortPerLevel = comfortPerLevel;
        this.capacityPerLevel = capacityPerLevel;
        this.upgradeCostByLevel = defaultCosts(name());
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
