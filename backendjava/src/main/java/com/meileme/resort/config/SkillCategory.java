package com.meileme.resort.config;

import lombok.Getter;

/** 技能大类（六大类，效果仅两类：倍率加成 / 费用减免）。 */
@Getter
public enum SkillCategory {

    BUILDING("建筑进化"),
    INTERIOR("内装"),
    DEMAND("客流"),
    HIRING("雇佣人力"),
    FINANCE("财务结算"),
    REGION("区块解锁");

    private final String displayName;

    SkillCategory(String displayName) {
        this.displayName = displayName;
    }
}
