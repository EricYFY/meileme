package com.meileme.resort.config;

/** 进化线：分支进化选定后不可切换、不可回退。 */
public enum EvolutionLine {

    /** 基础形态（母本原始形态，尚未选定分支） */
    BASE("基础"),
    /** 家庭亲子线 */
    FAMILY("家庭亲子线"),
    /** 商务行政线 */
    BUSINESS("商务行政线"),
    /** 合并进化产物线（由 MERGE_EVOLVE 生成，专属形态） */
    MERGE("合并进化线");

    private final String displayName;

    EvolutionLine(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
