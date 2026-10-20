package com.meileme.resort.model;

import lombok.Data;

/**
 * 里程碑记录（地块解锁等成长节点），用于前端里程碑列表展示。
 */
@Data
public class Milestone {

    /** 里程碑唯一 ID */
    private String id;

    /** 里程碑描述 */
    private String description;

    /** 奖励技能点 */
    private int skillPoints;

    /** 达成的游戏日 */
    private int day;
}