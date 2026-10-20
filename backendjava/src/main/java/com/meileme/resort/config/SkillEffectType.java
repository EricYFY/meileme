package com.meileme.resort.config;

import lombok.Getter;

/**
 * 技能效果类型（仅两类：倍率加成 / 费用减免）。
 *
 * <p>聚合口径：
 * <ul>
 *   <li>{@code MULTIPLIER}：同类效果相乘，最终倍率 = Π(1 + value)；</li>
 *   <li>{@code COST_DISCOUNT}：同类折扣相乘，最终折扣 = Π(1 − value)；</li>
 *   <li>{@code FLAT}：同类效果求和，最终附加值 = Σ value。</li>
 * </ul>
 */
@Getter
public enum SkillEffectType {

    MULTIPLIER("倍率加成"),
    COST_DISCOUNT("费用减免"),
    FLAT("固定加成");

    private final String displayName;

    SkillEffectType(String displayName) {
        this.displayName = displayName;
    }
}
