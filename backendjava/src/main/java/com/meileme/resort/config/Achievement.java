package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 成就。
 *
 * <p>成就为全局跨区域累计统计（新区域不重置），用于解锁地块与大区域。</p>
 */
@Getter
@AllArgsConstructor
public class Achievement {

    /** 成就唯一 ID */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 成就大类 */
    private final AchievementCategory category;
    /** 判定指标类型 */
    private final AchievementType type;
    /** 达成阈值 */
    private final double target;
    /** 附加参数（形态等级 / 客群 / 岗位等级等，可为空字符串） */
    private final String param;
}
