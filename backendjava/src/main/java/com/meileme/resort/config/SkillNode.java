package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 局内技能树节点。
 *
 * <p>拓扑为 DAG，<b>绝对禁止循环依赖</b>；每个技能点支持 1~N 个前置技能点，
 * 全部前置解锁后才可解锁当前技能；不支持洗点重置。
 * 技能点唯一来源为「达成局内经营里程碑」（当前设计：每解锁一个地块获得 1 点）。</p>
 */
@Getter
public class SkillNode {

    /** 技能唯一 ID */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 效果说明 */
    private final String description;
    /** 技能大类 */
    private final SkillCategory category;
    /** 前置技能 ID 列表（全部解锁后才可解锁本节点；空表示无前置） */
    private final List<String> prerequisites;
    /** 效果类型（倍率加成 / 费用减免 / 固定加成） */
    private final SkillEffectType effectType;
    /** 效果作用键 */
    private final SkillEffectKey effectKey;
    /** 效果数值（倍率类为 0.2 表示 +20%；减免类为 0.2 表示 −20%） */
    private final double value;
    /** 解锁需要消耗的技能点数（默认 1） */
    private final int costPoints;

    public SkillNode(String id, String displayName, String description, SkillCategory category,
                     List<String> prerequisites, SkillEffectType effectType, SkillEffectKey effectKey, double value) {
        this(id, displayName, description, category, prerequisites, effectType, effectKey, value, 1);
    }

    public SkillNode(String id, String displayName, String description, SkillCategory category,
                     List<String> prerequisites, SkillEffectType effectType, SkillEffectKey effectKey, double value, int costPoints) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.prerequisites = prerequisites;
        this.effectType = effectType;
        this.effectKey = effectKey;
        this.value = value;
        this.costPoints = Math.max(1, costPoints);
    }
}
