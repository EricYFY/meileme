package com.meileme.resort.config;

import lombok.Getter;

/** 大区域：每个大区域下划分多个地块，达成总成就后解锁下一区域。 */
@Getter
public enum Region {

    /** A 村（开局区域） */
    VILLAGE_A("A村"),
    /** B 镇（A 村总成就达成后解锁） */
    TOWN_B("B镇");

    private final String displayName;

    Region(String displayName) {
        this.displayName = displayName;
    }
}
