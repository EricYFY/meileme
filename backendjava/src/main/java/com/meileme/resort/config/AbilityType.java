package com.meileme.resort.config;

import lombok.Getter;

/**
 * 能力维度。全局共享人力池按能力维度汇总「员工总供给能力 vs 全部建筑总需求能力」；
 * 供给不足仅降低相关建筑舒适度，不削减承载容量。
 */
@Getter
public enum AbilityType {

    /** 客房清洁（客房保洁） */
    ROOM_CLEANING("客房清洁"),
    /** 客房维修（综合维修员） */
    ROOM_REPAIR("客房维修"),
    /** 餐厅服务（餐厅服务员） */
    RESTAURANT_SERVICE("餐厅服务"),
    /** 烹饪（厨师） */
    COOKING("烹饪"),
    /** 温泉运营（温泉运维员） */
    HOT_SPRING_OPERATION("温泉运营"),
    /** 温泉维修（综合维修员） */
    HOT_SPRING_REPAIR("温泉维修"),
    /** 全局管理（店长，本期暂不启用加成） */
    GLOBAL_MANAGEMENT("全局管理");

    private final String displayName;

    AbilityType(String displayName) {
        this.displayName = displayName;
    }
}
