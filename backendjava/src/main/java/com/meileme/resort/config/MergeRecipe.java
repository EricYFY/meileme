package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合并进化配方（预设固定配方，支持跨母本合并）。
 *
 * <p>合并 = 消耗两个源建筑（逻辑删除、保留墓碑记录）→ 新建一个产物建筑，
 * 产物记录合并来源建筑 ID，并享有「参数叠加继承 + 合并后专属加成」。</p>
 */
@Getter
@AllArgsConstructor
public class MergeRecipe {

    /** 配方唯一 ID */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 源建筑 A 形态 */
    private final BuildingForm sourceFormA;
    /** 源建筑 A 要求的形态等级 */
    private final int sourceLevelA;
    /** 源建筑 B 形态 */
    private final BuildingForm sourceFormB;
    /** 源建筑 B 要求的形态等级 */
    private final int sourceLevelB;
    /** 产物形态 */
    private final BuildingForm resultForm;
    /** 产物初始形态等级 */
    private final int resultLevel;
    /** 合并进化一次性花费 */
    private final double mergeCost;
    /** 合并进化工期（游戏日，自开工次日起累计） */
    private final int mergeDays;

    /** 配方是否与给定的两个「形态 + 等级」组合匹配（A/B 可对调） */
    public boolean matches(BuildingForm formA, int levelA, BuildingForm formB, int levelB) {
        return (sourceFormA == formA && sourceLevelA == levelA && sourceFormB == formB && sourceLevelB == levelB)
                || (sourceFormA == formB && sourceLevelA == levelB && sourceFormB == formA && sourceLevelB == levelA);
    }
}
