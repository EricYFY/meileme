package com.meileme.backend.model;

import com.meileme.backend.config.BuildingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 建筑实体（成本/维护/容量等数值一律取自 BuildingType 枚举） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Building {

    /** 0 可用 / 1 施工中（次日 0 点转正式） / 2 停用（如水管爆裂当日不可用） */
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNDER_CONSTRUCTION = 1;
    public static final int STATUS_DISABLED  = 2;

    private String id;
    private BuildingType type;
    private String plotId;            // 所属地块 INITIAL / MOUNTAIN / LAKESIDE
    private Coordinate position;      // 临路可建造格坐标（可视化 + 园区路网锚点）
    private int status = STATUS_AVAILABLE;

    /** 供前端展示的中文类型名 */
    public String getTypeName() {
        return type == null ? null : type.getLabel();
    }
}
