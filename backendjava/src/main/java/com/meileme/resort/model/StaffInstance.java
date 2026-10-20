package com.meileme.resort.model;

import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.PositionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工实例。
 *
 * <p>单个员工归属一个岗位，拥有该岗位的 1~N 项能力，同时计入各项能力总供给；
 * 能力值由等级固定、无随机浮动；在岗累计工作时长（按游戏日）达标后自动升级。</p>
 */
@Data
public class StaffInstance {

    /** 员工唯一 ID */
    private String id;

    /** 岗位 */
    private PositionType position;

    /** 当前等级（Lv1~Lv3） */
    private int level = 1;

    /** 在岗累计游戏日（用于解雇补偿计算） */
    private int workDays = 0;

    /** 当前等级已在岗累计游戏日（用于自动升级判定） */
    private int daysAtCurrentLevel = 0;

    /** 是否在册（解雇后逻辑删除） */
    private boolean alive = true;

    /** 入职生效的游戏日 */
    private int hireDay;

    /** 该员工提供的能力列表 */
    public List<AbilityType> abilities() {
        return position == null ? new ArrayList<>() : position.getAbilities();
    }

    /** 该员工对单项能力的供给值（能力值由等级固定） */
    public double abilityValue() {
        return position == null ? 0d : position.abilityValueOf(level);
    }
}
