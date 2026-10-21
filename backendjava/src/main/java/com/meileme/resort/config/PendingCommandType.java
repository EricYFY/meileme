package com.meileme.resort.config;

import lombok.Getter;

/**
 * 待生效指令类型。
 *
 * <p>所有玩家操作统一进入「待生效指令队列」，于<b>次日 0 点</b>统一生效并二次校验。</p>
 */
@Getter
public enum PendingCommandType {

    /** 新建建筑 */
    BUILD("新建建筑"),
    /** 建筑形态等级升级（Lv1 → Lv2 → Lv3） */
    FORM_LEVEL_UP("形态等级升级"),
    /** 分支进化（单体进化到当前进化线的下一阶段形态） */
    BRANCH_EVOLVE("分支进化"),
    /** 合并进化（按预设配方消耗两个源建筑，新建一个产物） */
    MERGE_EVOLVE("合并进化"),
    /** 内装升级 */
    INTERIOR_UPGRADE("内装升级"),
    /** 招聘员工 */
    HIRE_STAFF("招聘员工"),
    /** 解雇员工 */
    FIRE_STAFF("解雇员工"),
    /** 解锁局内技能点 */
    SKILL_UNLOCK("解锁技能"),
    /** 付费解锁地块 */
    UNLOCK_PLOT("解锁地块");

    private final String displayName;

    PendingCommandType(String displayName) {
        this.displayName = displayName;
    }
}
