package com.meileme.backend.config;

/** 随机事件类型（STAFF_ERROR 为员工失误产生的附加事件） */
public enum EventType {
    RAINSTORM  ("暴雨"),
    PIPE_BURST ("水管爆裂"),
    INFLUENCER ("网红打卡"),
    COMPLAINT  ("游客投诉"),
    LOCAL_TOUR ("本地旅游团"),
    MINOR_FAULT("设施小故障"),
    POSITIVE_REVIEW ("好评如潮"),
    RETURNING_GUEST ("回头客推荐"),
    PASSIVE_REP ("口碑自然提升"),
    TEAM_SUCCESS ("团建履约成功"),
    STAFF_ERROR("员工失误");

    private final String label;

    EventType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
