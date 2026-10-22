package com.meileme.resort.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 地块地图：由 width × height 个 {@link MapCell} 构成的二维网格。
 *
 * <p>地图的地形与可建造格布局在每局开始时由固定种子确定生成，稳定性可复现；
 * 建筑落格 / 相邻合并等运行时占用关系由建筑实例与格子坐标维护，不属于地图本身。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlotMap {

    /** 所属地块 ID */
    private String plotId;

    /** 地图宽度（列数） */
    private int width;

    /** 地图高度（行数） */
    private int height;

    /** 逐格数据（按 row-major：y=0 行从左到右，再 y=1 行… 排列） */
    private List<MapCell> cells;

    /** 按坐标查找格子（越界或未找到返回 null） */
    public MapCell cellAt(int x, int y) {
        if (cells == null) {
            return null;
        }
        for (MapCell c : cells) {
            if (c.getX() == x && c.getY() == y) {
                return c;
            }
        }
        return null;
    }

    /** 可建造格数量 */
    public int buildableCount() {
        if (cells == null) {
            return 0;
        }
        int n = 0;
        for (MapCell c : cells) {
            if (c.isBuildable()) {
                n++;
            }
        }
        return n;
    }
}