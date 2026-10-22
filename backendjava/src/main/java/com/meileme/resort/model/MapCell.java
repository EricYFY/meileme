package com.meileme.resort.model;

import com.meileme.resort.config.TerrainType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地块地图上的单个格子。
 *
 * <p>{@code buildable = true} 表示该格为可建造空地（此时 {@code terrain} 为 {@code null}）；
 * {@code buildable = false} 表示纯地形格，{@code terrain} 为随机地形（城镇 / 平原 / 山地 / 湖区）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapCell {

    /** 列坐标（从 0 开始，向右递增） */
    private int x;

    /** 行坐标（从 0 开始，向下递增） */
    private int y;

    /** 地形类型；可建造格为 null */
    private TerrainType terrain;

    /** 是否为可建造空地 */
    private boolean buildable;
}