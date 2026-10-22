package com.meileme.resort.config;

import lombok.Getter;

/** 地块地图上的地形类型。仅用于不可建造格子的展示纹理划分。 */
@Getter
public enum TerrainType {

    TOWN("城镇"),
    PLAIN("平原"),
    MOUNTAIN("山地"),
    LAKE("湖区");

    private final String displayName;

    TerrainType(String displayName) {
        this.displayName = displayName;
    }
}