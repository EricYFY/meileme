package com.meileme.resort.config;

import lombok.Getter;

/**
 * 建筑母本清单（6 种住宿母本 + 9 种配套母本）。
 *
 * <p>本期仅「标准客房」母本可建造（{@link #isPhase1Buildable()}），其余母本仅登记、不可建；
 * 具体某地块能否建造还受 {@link Plot} 白名单约束。</p>
 */
@Getter
public enum MotherBuildingType {

    // ============ 住宿母本（6） ============
    /** 标准客房：第一期唯一可建造母本，拥有家庭亲子线 / 商务行政线两条进化分支 */
    STANDARD_ROOM("标准客房", BuildingCategory.ACCOMMODATION, true),
    /** 豪华客房（仅登记） */
    LUXURY_ROOM("豪华客房", BuildingCategory.ACCOMMODATION, false),
    /** 独栋木屋（仅登记） */
    WOODEN_CABIN("独栋木屋", BuildingCategory.ACCOMMODATION, false),
    /** 复式 LOFT（仅登记） */
    DUPLEX_LOFT("复式LOFT", BuildingCategory.ACCOMMODATION, false),
    /** 庭院小院（仅登记） */
    COURTYARD_HOUSE("庭院小院", BuildingCategory.ACCOMMODATION, false),
    /** 多卧平房（仅登记） */
    MULTI_BEDROOM_HOUSE("多卧平房", BuildingCategory.ACCOMMODATION, false),

    // ============ 配套母本（9，均仅登记、第一期不可建） ============
    /** 餐厅 */
    RESTAURANT("餐厅", BuildingCategory.SUPPORT, false),
    /** 温泉泡池 */
    HOT_SPRING_POOL("温泉泡池", BuildingCategory.SUPPORT, false),
    /** 观景台 */
    VIEWING_PLATFORM("观景台", BuildingCategory.SUPPORT, false),
    /** 接待大堂 */
    RECEPTION_LOBBY("接待大堂", BuildingCategory.SUPPORT, false),
    /** 茶室书吧 */
    TEA_LIBRARY("茶室书吧", BuildingCategory.SUPPORT, false),
    /** 文创工坊 */
    CULTURE_WORKSHOP("文创工坊", BuildingCategory.SUPPORT, false),
    /** 后勤库房 */
    LOGISTICS_WAREHOUSE("后勤库房", BuildingCategory.SUPPORT, false),
    /** 户外营地 */
    OUTDOOR_CAMP("户外营地", BuildingCategory.SUPPORT, false),
    /** 亲子乐园 */
    PARENT_CHILD_PARK("亲子乐园", BuildingCategory.SUPPORT, false);

    /** 展示名 */
    private final String displayName;
    /** 建筑大类 */
    private final BuildingCategory category;
    /** 第一期是否开放建造（仅标准客房为 true） */
    private final boolean phase1Buildable;

    MotherBuildingType(String displayName, BuildingCategory category, boolean phase1Buildable) {
        this.displayName = displayName;
        this.category = category;
        this.phase1Buildable = phase1Buildable;
    }
}
