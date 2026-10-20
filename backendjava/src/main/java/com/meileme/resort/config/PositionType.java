package com.meileme.resort.config;

import lombok.Getter;

import java.util.List;

/**
 * 岗位与能力映射。
 *
 * <p>单个员工归属于一个岗位，并拥有该岗位对应的 1~N 项能力（复合能力员工同时计入多项能力总供给）。
 * 能力值由等级决定、无随机浮动；薪资同样按岗位 + 等级固定。</p>
 */
@Getter
public enum PositionType {

    /** 客房保洁 → 客房清洁 */
    ROOM_CLEANER("客房保洁", List.of(AbilityType.ROOM_CLEANING),
            new double[]{100, 160, 240}, new double[]{30, 55, 85},
            400, 10, 20, 600),

    /** 餐厅服务员 → 餐厅服务 */
    WAITER("餐厅服务员", List.of(AbilityType.RESTAURANT_SERVICE),
            new double[]{110, 170, 250}, new double[]{30, 55, 85},
            400, 10, 20, 600),

    /** 厨师 → 烹饪 */
    CHEF("厨师", List.of(AbilityType.COOKING),
            new double[]{200, 320, 460}, new double[]{35, 60, 90},
            800, 12, 24, 1200),

    /** 温泉运维员 → 温泉运营 */
    HOT_SPRING_OPERATOR("温泉运维员", List.of(AbilityType.HOT_SPRING_OPERATION),
            new double[]{130, 200, 290}, new double[]{30, 55, 85},
            500, 10, 20, 700),

    /** 综合维修员 → 客房维修 + 温泉维修（复合能力） */
    MAINTENANCE_WORKER("综合维修员", List.of(AbilityType.ROOM_REPAIR, AbilityType.HOT_SPRING_REPAIR),
            new double[]{150, 230, 330}, new double[]{25, 45, 70},
            600, 12, 24, 900),

    /** 店长 → 全局管理（本期暂不启用加成） */
    MANAGER("店长", List.of(AbilityType.GLOBAL_MANAGEMENT),
            new double[]{300, 450, 650}, new double[]{40, 70, 100},
            1500, 15, 30, 2000);

    /** 展示名 */
    private final String displayName;
    /** 该岗位提供的能力（1~N 项，复合能力同时计入） */
    private final List<AbilityType> abilities;
    /** 各等级每日薪资（下标 0 = Lv1） */
    private final double[] salaryByLevel;
    /** 各等级能力值（下标 0 = Lv1，作用于该岗位的每一项能力） */
    private final double[] abilityValueByLevel;
    /** 招聘该岗位 Lv1 的一次性花费 */
    private final double hireCost;
    /** Lv1 → Lv2 所需在岗累计游戏日 */
    private final int upgradeDaysLv1ToLv2;
    /** Lv2 → Lv3 所需在岗累计游戏日 */
    private final int upgradeDaysLv2ToLv3;
    /** 解雇补偿基准值（实际补偿按岗位 + 等级 + 在岗累计时长计算） */
    private final double compensationBase;

    PositionType(String displayName, List<AbilityType> abilities,
                 double[] salaryByLevel, double[] abilityValueByLevel,
                 double hireCost, int upgradeDaysLv1ToLv2, int upgradeDaysLv2ToLv3,
                 double compensationBase) {
        this.displayName = displayName;
        this.abilities = abilities;
        this.salaryByLevel = salaryByLevel;
        this.abilityValueByLevel = abilityValueByLevel;
        this.hireCost = hireCost;
        this.upgradeDaysLv1ToLv2 = upgradeDaysLv1ToLv2;
        this.upgradeDaysLv2ToLv3 = upgradeDaysLv2ToLv3;
        this.compensationBase = compensationBase;
    }

    /** 指定等级每日薪资 */
    public double salaryOf(int level) {
        int idx = clampLevelIndex(level);
        return salaryByLevel[idx];
    }

    /** 指定等级的单能力能力值 */
    public double abilityValueOf(int level) {
        int idx = clampLevelIndex(level);
        return abilityValueByLevel[idx];
    }

    /** 从指定等级升到下一级所需的在岗累计游戏日；已满级返回 0 */
    public int workDaysToNextLevel(int level) {
        if (level <= 1) {
            return upgradeDaysLv1ToLv2;
        }
        if (level == 2) {
            return upgradeDaysLv2ToLv3;
        }
        return 0;
    }

    /** 招聘指定等级的一次性花费（按等级线性放大） */
    public double hireCostOf(int level) {
        return hireCost * clampLevelIndex(level) + hireCost;
    }

    /** 解雇一次性补偿金：岗位类型 × 等级 × 在岗累计时长（复合能力员工按主类型计算，不叠加） */
    public double compensationOf(int level, int workDays) {
        double levelFactor = clampLevelIndex(level) + 1;
        double dayFactor = 1 + Math.max(0, workDays) * GameConfig.FIRE_COMPENSATION_PER_DAY_RATIO;
        return compensationBase * levelFactor * dayFactor;
    }

    private int clampLevelIndex(int level) {
        int idx = level - GameConfig.STAFF_MIN_LEVEL;
        if (idx < 0) {
            idx = 0;
        }
        if (idx > salaryByLevel.length - 1) {
            idx = salaryByLevel.length - 1;
        }
        return idx;
    }
}
