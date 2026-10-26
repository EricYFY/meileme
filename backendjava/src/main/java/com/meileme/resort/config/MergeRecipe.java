package com.meileme.resort.config;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 合并进化配方（预设固定配方，支持跨母本合并）。
 *
 * <p>合并 = 消耗两个源建筑（逻辑删除、保留墓碑记录）→ 新建一个产物建筑，
 * 产物记录合并来源建筑 ID，并享有「参数叠加继承 + 合并后专属加成」。</p>
 */
@Getter
public class MergeRecipe {

    /** 配方唯一 ID */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 需求源建筑列表（形态 + 要求的最低形态等级） */
    private final List<SourceRequirement> sources;
    /** 产物形态 */
    private final BuildingForm resultForm;
    /** 产物初始形态等级 */
    private final int resultLevel;
    /** 合并进化一次性花费 */
    private final double mergeCost;
    /** 合并进化工期（游戏日，自开工次日起累计） */
    private final int mergeDays;

    public MergeRecipe(String id, String displayName, List<SourceRequirement> sources, BuildingForm resultForm, int resultLevel, double mergeCost, int mergeDays) {
        this.id = id;
        this.displayName = displayName;
        this.sources = sources;
        this.resultForm = resultForm;
        this.resultLevel = resultLevel;
        this.mergeCost = mergeCost;
        this.mergeDays = mergeDays;
    }

    /** 兼容历史双形态构造函数 */
    public MergeRecipe(String id, String displayName, BuildingForm formA, int levelA, BuildingForm formB, int levelB,
                       BuildingForm resultForm, int resultLevel, double mergeCost, int mergeDays) {
        this(id, displayName, List.of(new SourceRequirement(formA, levelA), new SourceRequirement(formB, levelB)),
                resultForm, resultLevel, mergeCost, mergeDays);
    }

    public static class SourceRequirement {
        private final BuildingForm form;
        private final int minLevel;

        public SourceRequirement(BuildingForm form, int minLevel) {
            this.form = form;
            this.minLevel = minLevel;
        }

        public BuildingForm getForm() {
            return form;
        }

        public int getMinLevel() {
            return minLevel;
        }
    }

    public List<SourceRequirement> getSources() {
        return sources != null ? sources : Collections.emptyList();
    }

    public BuildingForm getSourceFormA() {
        return (sources != null && sources.size() > 0) ? sources.get(0).getForm() : null;
    }

    public int getSourceLevelA() {
        return (sources != null && sources.size() > 0) ? sources.get(0).getMinLevel() : 1;
    }

    public BuildingForm getSourceFormB() {
        return (sources != null && sources.size() > 1) ? sources.get(1).getForm() : null;
    }

    public int getSourceLevelB() {
        return (sources != null && sources.size() > 1) ? sources.get(1).getMinLevel() : 1;
    }

    /** 配方是否与给定的两个「形态 + 等级」组合匹配（A/B 可对调） */
    public boolean matches(BuildingForm formA, int levelA, BuildingForm formB, int levelB) {
        if (sources == null || sources.size() < 2) return false;
        SourceRequirement req0 = sources.get(0);
        SourceRequirement req1 = sources.get(1);
        boolean order1 = (req0.getForm() == formA && levelA >= req0.getMinLevel()
                && req1.getForm() == formB && levelB >= req1.getMinLevel());
        boolean order2 = (req0.getForm() == formB && levelB >= req0.getMinLevel()
                && req1.getForm() == formA && levelA >= req1.getMinLevel());
        return order1 || order2;
    }
}
