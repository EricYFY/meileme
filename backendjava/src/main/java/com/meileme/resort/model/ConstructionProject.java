package com.meileme.resort.model;

import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.ProjectType;
import lombok.Data;

/**
 * 进行中的工程。
 *
 * <p>玩家点击后立即创建并扣除一次性花费，工期从<b>次日</b>开始按游戏日累计；
 * 累计达标后才写入建筑状态（新建 / 升级 / 进化 / 合并 / 内装），期间一切经营数据沿用工程开始前的状态。</p>
 */
@Data
public class ConstructionProject {

    /** 工程 ID */
    private String id;

    /** 工程类型 */
    private ProjectType type;

    /** 描述（展示用） */
    private String displayName;

    /** 涉及地块（新建用；其余取建筑所在地块） */
    private String plotId;

    /** 主目标建筑（新建时为空） */
    private String buildingId;

    /** 合并进化的第二个源建筑 */
    private String secondBuildingId;

    /** 新建母本 */
    private MotherBuildingType mother;

    /** 分支进化目标形态 ID */
    private String targetFormId;

    /** 内装类型 */
    private InteriorType interiorType;

    /** 形态等级升级 / 内装升级的目标等级 */
    private int targetLevel;

    /** 总工期（游戏日） */
    private int totalDays;

    /** 剩余工期（游戏日） */
    private int remainingDays;

    /** 开始日 */
    private int startedDay;

    /** 开工时已支付的一次性花费（工程失败时原额退还） */
    private double paidCost;

    /** 已完成累计天数 */
    public int elapsedDays() {
        return Math.max(0, totalDays - remainingDays);
    }

    /** 预计完成日（= 开始日 + 总工期） */
    public int finishDay() {
        return startedDay + totalDays;
    }
}
