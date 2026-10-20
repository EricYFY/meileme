package com.meileme.resort.config;

import lombok.Getter;

/** 成就大类：财务 / 建筑 / 客流 / 雇佣（全局跨区域累计统计，新区域不重置）。 */
@Getter
public enum AchievementCategory {

    FINANCE("财务"),
    BUILDING("建筑"),
    GUEST("客流"),
    STAFF("雇佣");

    private final String displayName;

    AchievementCategory(String displayName) {
        this.displayName = displayName;
    }
}
