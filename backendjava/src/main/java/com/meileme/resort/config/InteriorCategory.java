package com.meileme.resort.config;

import lombok.Getter;

/** 内装分类：承载型同时提升承载容量 + 舒适度；普通型仅提升舒适度。 */
@Getter
public enum InteriorCategory {

    /** 承载型内装（床位、餐桌、泡池等） */
    CARRY("承载型"),
    /** 普通内装（仅提升舒适度） */
    NORMAL("普通");

    private final String displayName;

    InteriorCategory(String displayName) {
        this.displayName = displayName;
    }
}
