package com.meileme.resort.config;

/** 进行中的工程类型（玩家点击后进入对应状态，按工期完成后才生效）。 */
public enum ProjectType {

    /** 新建建筑 */
    BUILD("建造中"),
    /** 形态等级升级 */
    FORM_LEVEL_UP("升级中"),
    /** 分支进化 */
    BRANCH_EVOLVE("进化中"),
    /** 合并进化 */
    MERGE_EVOLVE("合并中"),
    /** 内装升级 */
    INTERIOR_UPGRADE("内装升级中");

    private final String displayName;

    ProjectType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
