package com.meileme.resort.config;

/** 建筑大类：住宿 / 配套。 */
public enum BuildingCategory {

    /** 住宿类母本 */
    ACCOMMODATION("住宿"),
    /** 配套类母本 */
    SUPPORT("配套");

    private final String displayName;

    BuildingCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
