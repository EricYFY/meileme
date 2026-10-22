package com.meileme.resort.config;

import lombok.Getter;

/**
 * 待生效指令类型（即时类操作）。
 *
 * <p>招聘 / 培训 / 解雇 / 技能 / 地块解锁统一进入「待生效指令队列」，于<b>次日 0 点</b>统一生效并二次校验；
 * 建造 / 升级 / 进化 / 合并 / 内装改为「进行中工程」（见 {@code ConstructionProject}），按工期完成后生效。</p>
 */
@Getter
public enum PendingCommandType {

    /** 招聘员工（仅 Lv1） */
    HIRE_STAFF("招聘员工"),
    /** 培训升级员工 */
    TRAIN_STAFF("培训员工"),
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
