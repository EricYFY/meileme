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

    /** 0 可用 / 1 施工中（次日 0 点转正式） / 2 停用 */
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNDER_CONSTRUCTION = 1;
    public static final int STATUS_DISABLED  = 2;

    /**
     * 停用来源（仅 status=2 时有意义）：
     *   AUTO   —— 系统自动停用（水管爆裂），仅当日临时失效，仍扣维护费，次日自动恢复；
     *   MANUAL —— 玩家手动停用，持续有效，不产生容量/收入且不扣维护费，需玩家手动启用。
     */
    public enum DisableSource { AUTO, MANUAL }

    private String id;
    private BuildingType type;
    private String plotId;            // 所属地块 INITIAL / MOUNTAIN / LAKESIDE
    private Coordinate position;      // 临路可建造格坐标（可视化 + 园区路网锚点）
    private int status = STATUS_AVAILABLE;
    private DisableSource disableSource; // 停用来源，恢复运营时清空

    /** 是否为玩家手动停用（区别于水管爆裂自动停用：不影响每日维护费扣除） */
    public boolean isManuallyDisabled() {
        return status == STATUS_DISABLED && disableSource == DisableSource.MANUAL;
    }

    /** 是否为系统自动停用（水管爆裂，次日自动恢复） */
    public boolean isAutoDisabled() {
        return status == STATUS_DISABLED && disableSource == DisableSource.AUTO;
    }

    /** 供前端展示的中文类型名 */
    public String getTypeName() {
        return type == null ? null : type.getLabel();
    }
}
