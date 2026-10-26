package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 地块：每个大区域下划分多个地块。
 *
 * <p>每个地块独立配置：可建造母本白名单、地块内分支进化进阶上限。
 * 开局仅开放史东薇尔城；后续地块须「上一地块已解锁 + 达成对应成就」后，再<b>支付解锁费用</b>方可解锁。</p>
 */
@Getter
@AllArgsConstructor
public class Plot {

    /** 地块唯一 ID */
    private final String id;
    /** 展示名 */
    private final String displayName;
    /** 所属大区域 */
    private final Region region;
    /** 区域内排序（决定解锁顺序） */
    private final int orderInRegion;
    /** 地图宽度（列数） */
    private final int width;
    /** 地图高度（行数） */
    private final int height;
    /** 地图上预挖可建造格数量 */
    private final int buildableCellCount;
    /** 解锁该地块需支付的一次性费用（0 = 免费，仅默认地块） */
    private final double unlockCost;
    /** 可建造母本白名单 */
    private final Set<MotherBuildingType> allowedMothers;
    /** 地块内分支进化进阶上限（形态 tier 不得超过此值） */
    private final int evolveTierCap;
    /** 解锁所需成就 ID（全部达成才解锁；空表示默认解锁） */
    private final List<String> unlockAchievementIds;
    /** 是否为开局默认解锁地块 */
    private final boolean unlockedByDefault;

    /** 该母本是否允许在本地块建造 */
    public boolean allows(MotherBuildingType mother) {
        if (mother == null) return false;
        return allowedMothers != null && allowedMothers.contains(mother);
    }

    public Set<MotherBuildingType> getAllowedMothers() {
        return allowedMothers != null ? allowedMothers : Collections.emptySet();
    }
}
