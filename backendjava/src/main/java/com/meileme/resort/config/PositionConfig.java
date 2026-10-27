package com.meileme.resort.config;

import lombok.Builder;
import lombok.Getter;

import java.util.*;

/**
 * 岗位配置模型（数据驱动，来源于 admin-config.json / default-config.json）。
 */
@Getter
@Builder(toBuilder = true)
public class PositionConfig {

    private final String id;
    private final String displayName;
    /** 各能力对应的各等级输出值 Map<Ability, [Lv1, Lv2, Lv3...]> */
    private final Map<AbilityType, double[]> abilityOutputs;
    /** Lv1 招聘花费 */
    private final double hireCost;
    /** 各等级升级在岗天数列表 [1->2, 2->3, 3->4...] */
    private final int[] upgradeDaysByLevel;
    /** 各等级升级培训工期天数列表 [1->2, 2->3, ...] */
    private final int[] trainDaysByLevel;
    /** 各等级升级培训费用列表 [1->2, 2->3, ...] */
    private final double[] trainCostByLevel;
    /** 解雇补偿基准值 */
    private final double compensationBase;
    /** 典藏图鉴背景故事 */
    private final String story;

    public List<AbilityType> getAbilities() {
        if (abilityOutputs != null && !abilityOutputs.isEmpty()) {
            return new ArrayList<>(abilityOutputs.keySet());
        }
        return Collections.emptyList();
    }

    public double[] getAbilityValueByLevel() {
        if (abilityOutputs != null && !abilityOutputs.isEmpty()) {
            return abilityOutputs.values().iterator().next();
        }
        return new double[]{30, 55, 85};
    }

    /** 从指定等级升到下一级所需的在岗天数 */
    public int workDaysToNextLevel(int level) {
        if (upgradeDaysByLevel != null && upgradeDaysByLevel.length > 0) {
            int idx = level - 1;
            if (idx >= 0 && idx < upgradeDaysByLevel.length) {
                return upgradeDaysByLevel[idx];
            }
            return upgradeDaysByLevel[upgradeDaysByLevel.length - 1] + (level - upgradeDaysByLevel.length) * 10;
        }
        return level <= 1 ? 10 : 20;
    }

    /** 从指定等级培训升到下一级所需的培训工期天数 */
    public int trainDaysToNextLevel(int level) {
        if (trainDaysByLevel != null && trainDaysByLevel.length > 0) {
            int idx = level - 1;
            if (idx >= 0 && idx < trainDaysByLevel.length) {
                return trainDaysByLevel[idx];
            }
            return trainDaysByLevel[trainDaysByLevel.length - 1];
        }
        return level <= 1 ? 1 : 2;
    }

    public int[] getTrainDaysByLevel() {
        if (trainDaysByLevel != null && trainDaysByLevel.length > 0) {
            return trainDaysByLevel;
        }
        return new int[]{1, 2};
    }

    /** 招聘指定等级花费 */
    public double hireCostOf(int level) {
        int idx = Math.max(0, level - 1);
        return hireCost * idx + hireCost;
    }

    /** 升级培训花费（等级越高越贵） */
    public double trainCostOf(int currentLevel) {
        int idx = Math.max(0, currentLevel - 1);
        if (trainCostByLevel != null && trainCostByLevel.length > 0) {
            return idx < trainCostByLevel.length ? trainCostByLevel[idx] : trainCostByLevel[trainCostByLevel.length - 1];
        }
        return hireCost * 0.6 * currentLevel;
    }

    public double[] getTrainCostByLevel() {
        if (trainCostByLevel != null && trainCostByLevel.length > 0) {
            return trainCostByLevel;
        }
        return new double[]{hireCost * 0.6, hireCost * 1.2};
    }

    /** 解雇补偿计算 */
    public double compensationOf(int level, int workDays) {
        int safeLevel = Math.max(1, level);
        int safeWorkDays = Math.max(0, workDays);
        return compensationBase * safeLevel + (safeWorkDays / 30.0) * compensationBase * 0.2;
    }

    /** 指定能力和等级的输出值 */
    public double abilityValueOf(AbilityType ability, int level) {
        int idx = Math.max(0, level - 1);
        if (ability != null && abilityOutputs != null && abilityOutputs.containsKey(ability)) {
            double[] arr = abilityOutputs.get(ability);
            if (arr != null && arr.length > 0) {
                return idx < arr.length ? arr[idx] : arr[arr.length - 1];
            }
        }
        if (abilityOutputs != null && !abilityOutputs.isEmpty()) {
            double[] arr = abilityOutputs.values().iterator().next();
            if (arr != null && arr.length > 0) {
                return idx < arr.length ? arr[idx] : arr[arr.length - 1];
            }
        }
        return level == 1 ? 30.0 : (level == 2 ? 55.0 : 85.0);
    }

    /** 指定等级的基础能力输出值 */
    public double abilityValueOf(int level) {
        return abilityValueOf(null, level);
    }
}
