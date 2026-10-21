package com.meileme.resort.config;

import lombok.Getter;

/**
 * 技能效果作用键（业务侧按此键取值，详见 {@code SkillTreeService}）。
 *
 * <p>技能效果仅两类：倍率加成 / 费用减免；本枚举覆盖六大技能大类下的全部作用点。</p>
 */
@Getter
public enum SkillEffectKey {

    // —— 建筑进化类 ——
    /** 新建建筑费用减免 */
    BUILD_COST_DISCOUNT("新建建筑费用"),
    /** 建筑形态进化（分支进化）费用减免 */
    EVOLVE_COST_DISCOUNT("形态进化费用"),
    /** 合并进化专属加成（合并产物房屋收益加成） */
    MERGE_BONUS("合并专属加成"),
    /** 合并进化消耗减免 */
    MERGE_COST_DISCOUNT("合并进化消耗"),
    /** 单地块可建造建筑数量上限加成 */
    PLOT_BUILD_LIMIT("地块建筑上限"),

    // —— 内装类 ——
    /** 内装升级费用减免 */
    INTERIOR_UPGRADE_COST_DISCOUNT("内装升级费用"),
    /** 内装对舒适度的加成倍率 */
    INTERIOR_COMFORT_MULT("内装舒适度加成"),
    /** 内装对承载容量的加成倍率 */
    INTERIOR_CAPACITY_MULT("内装承载加成"),

    // —— 客流类 ——
    /** 分客群吸引力倍率 */
    SEGMENT_ATTRACTION_MULT("分客群吸引力"),
    /** 客群转化率倍率 */
    CONVERSION_RATE_MULT("客群转化率"),

    // —— 雇佣人力类 ——
    /** 员工能力值全局倍率 */
    STAFF_ABILITY_MULT("员工能力值"),
    /** 员工自动升级所需在岗时长倍率（越小越快） */
    STAFF_UPGRADE_DAYS_MULT("员工升级所需时长"),
    /** 招聘消耗减免 */
    HIRE_COST_DISCOUNT("招聘消耗"),
    /** 解雇补偿减免 */
    FIRE_COMPENSATION_DISCOUNT("解雇补偿"),

    // —— 财务结算类 ——
    /** 房屋收益基准加成 */
    ROOM_PRICE_MULT("房屋收益基准"),
    /** 配套收益基准加成 */
    SUPPORT_PRICE_MULT("配套收益基准"),

    // —— 区块解锁类 ——
    /** 区块解锁所需成就门槛减免 */
    REGION_ACHIEVEMENT_DISCOUNT("区块解锁门槛");

    private final String displayName;

    SkillEffectKey(String displayName) {
        this.displayName = displayName;
    }
}
