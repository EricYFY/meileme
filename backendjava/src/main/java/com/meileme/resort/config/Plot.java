package com.meileme.resort.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * 地块：每个大区域下划分多个地块。
 *
 * <p>每个地块独立配置：可建造住宿母本白名单、可建造配套母本白名单、地块内分支进化进阶上限。
 * 开局仅开放 A 村乡间地块；达成指定成就后依次解锁同区域后续地块。</p>
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
    /** 地块内建筑数量上限 */
    private final int buildLimit;
    /** 可建造住宿母本白名单 */
    private final Set<MotherBuildingType> accommodationWhitelist;
    /** 可建造配套母本白名单 */
    private final Set<MotherBuildingType> supportWhitelist;
    /** 地块内分支进化进阶上限（形态 tier 不得超过此值） */
    private final int evolveTierCap;
    /** 解锁所需成就 ID（全部达成才解锁；空表示默认解锁） */
    private final List<String> unlockAchievementIds;
    /** 是否为开局默认解锁地块 */
    private final boolean unlockedByDefault;

    /** 该母本是否允许在本地块建造 */
    public boolean allows(MotherBuildingType mother) {
        if (mother.getCategory() == BuildingCategory.ACCOMMODATION) {
            return accommodationWhitelist.contains(mother);
        }
        return supportWhitelist.contains(mother);
    }
}
