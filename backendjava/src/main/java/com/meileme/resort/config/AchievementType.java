package com.meileme.resort.config;

import lombok.Getter;

/**
 * 成就判定指标类型。
 *
 * <p>部分类型需要额外参数（{@link Achievement#getParam()}）：
 * FORM_LEVEL_COUNT 需要 "形态ID_等级"，SEGMENT_GUESTS 需要客群名，POSITION_LEVEL_COUNT 需要 "岗位名_等级"。</p>
 */
@Getter
public enum AchievementType {

    /** 总资产 */
    TOTAL_ASSETS("总资产"),
    /** 累计总收入 */
    CUMULATIVE_INCOME("累计总收入"),
    /** 当前已建建筑总数量 */
    BUILDING_COUNT("已建建筑总数量"),
    /** 特定建筑 / 特定形态等级完成建设 */
    FORM_LEVEL_COUNT("特定形态等级建筑数"),
    /** 累计接待总人数 */
    CUMULATIVE_GUESTS("累计接待总人数"),
    /** 特定客群累计接待总数 */
    SEGMENT_GUESTS("特定客群累计接待总数"),
    /** 当前员工总人数 */
    STAFF_COUNT("员工总人数"),
    /** 拥有特定员工类型 / 特定员工等级 */
    POSITION_LEVEL_COUNT("特定岗位等级员工数");

    private final String displayName;

    AchievementType(String displayName) {
        this.displayName = displayName;
    }
}
